<?php 
/*---------------------------------------------------------------------------*
 * controllers/api_controller.php                                        *
 *                                                                           *
 * Contains functions that are used by the phone: push and pull to send a    *
 * subjects survey answers and pull new survey data, respectively.           *
 *---------------------------------------------------------------------------*/
/**
 * Controls communication between phones and the database.  Access to the push
 * and pull functions are not restricted to logged in users to allow the phones
 * to use them without being logged in.
 * 
 * @author Austin Walker
 * @author Tony Xiao
 */
class APIController extends AppController
{
	//for php4
	var $name = 'api';
	
	//this controller is associated with all the models that the phones use
	var $uses = array('Survey', 'Answer', 'Location', 'StatusChange', 'Call',
		'Subject', 'Configuration', 'SurveysTaken', 'Extra');
	var $helpers = array('Js' => 'jquery');
	var $layout = 'json';
	
	//allow anyone (eg the phones) to use push() and pull()
	var $components = array('Auth' => array
	(
		'authorize' => 'controller',
		'allowedActions' => array('push', 'pull', 'error', 'salt')
	));
    
	/**
	 * Override the app_controller beforeFilter() so we can expose error() via HTTP
	 */
	function beforeFilter()
	{
		//if the user has set the site to use ssl, force https connections
		if (SSL === true) $this->Ssl->force();
	}
	
	/**
	 * Returns the subject id if one can be found for the given device id. Sets
	 * message to be the error message if an error occurs and sets worked to
	 * false if an error occurs or true if not.
	 */
	function getSubjectId($deviceid, &$message, &$worked)
	{
		$worked = true;
		$message = NULL;
		if (!isset($deviceid))
		{
			$worked = false;
			$message = 'no device id given';
			return NULL;
		}
		else
		{
			//now, make sure the given deviceId is registered to a subject
			$subjectid = $this->Subject->find('first', array
			(
				'conditions' => array('device_id' => $deviceid, 'is_inactive' => false),
				'fields' => array('id')
			));
			$subjectid = $subjectid['Subject']['id'];
			if ($subjectid == NULL)
			{
				$worked = false;
				$message = 'invalid or unregistered device id';
			}
			return $subjectid;
		}
    }
	
	/**
	 * Pull survey data from the database and convert to JSON.
	 */
	function pull($deviceid = NULL)
	{
	    error_reporting(E_ERROR | E_WARNING | E_PARSE);
		$subjectid = $this->getSubjectID($deviceid, $message, $worked);
		if ($worked == false)
		{
			$this->set('result', $worked);
			$this->set('message', $message);
			return;
		}
		$results = array
		(
			'surveys' => array(),
			'questions' => array(),
			'choices' => array(),
			'branches' => array(),
			'conditions' => array(),
			'config' => array()
		);
		foreach (array('surveys', 'questions', 'choices', 'branches', 'conditions') as $table)
		{
			$result = $this->Survey->query("SELECT * from $table");
			foreach ($result as &$item)
			{
				//deal with special fields here
				foreach ($item[$table] as $field => &$value)
				{
					//Parse the String into a new UNIX Timestamp
					if ($field == 'created' || $field == 'modified' || $field == 'updated')
						// From http://snippets.dzone.com/posts/show/1455
						$value = strtotime($value . ' GMT');
					if ($field == 'subject_variables') {
					    $subjectVariables = json_decode($value, true);
                        if (isset($subjectVariables)) {
                            if (array_key_exists($subjectid, $subjectVariables)) {
                                $value = $subjectVariables[$subjectid];
                            } else {
                                $value = null;
                            }
                        } else {
                            $value = NULL;
                        }
					}
				}
				$results[$table] = array_merge($results[$table], array($item[$table]));
			}
		}
		//config is a bit different, so do that here:
		$result = $this->Configuration->query("SELECT c_key, c_value from configurations");
		foreach ($result as $item)
		{
			//TODO support the dot character in the names using '\.'
			$names = explode('.', $item['configurations']['c_key']);
			$results['config'] = $this->Configuration->array_inflate($results['config'], $names, $item['configurations']['c_value']);
		}
		$this->set('result', true);
		$this->set('results', $results);
	}
	
	/**
	 * Accepts a request continaing a JSON object with answers, locations,
	 * statuschanges, and calls and attepmts to parse that data and put it into
	 * the database.
	 */
	function push($deviceid = NULL)
	{
		//since the JSON object is in the body of the request, get the whole request text
		$info = file_get_contents('php://input');
		
		//array of models to look for data to save as [JSON name] => [CakePHP name]
		$models = array
		(
			'answers' => 'Answer',
			'locations' => 'Location',
			'calls' => 'Call',
			'statusChanges' => 'StatusChange',
			'surveysTaken' =>  "SurveysTaken",
			'extras' => 'Extra'
		);
		
		//assuming there is some textual JSON array in $info:
		$result = true; //did the push work?  changed to false on error
		$message = NULL; //if it didn't, why not?
		$info = json_decode($info, true);
		if ($info == NULL)
		{
			$this->set('result', false);
			$this->set('message', 'Invalid JSON');
			return;
		}
		$subjectid = $this->getSubjectID($deviceid, $message, $worked);
		if ($worked == false)
		{
			$this->set('result', $worked);
			$this->set('message', $message);
			return;
		}
		else
		{
			//now, go through the rest of the data
			foreach ($info as $table => $items)
			{
				foreach ($models as $json_name => $cake_name)
				{ //TODO this can be more efficent
					if ($table == $json_name)
					{
						foreach ($items as $item)
						{
							$toSave = array();
							foreach ($item as $key => $val)
							{
								//turn Unix timestamps into MySQL DATETIME format
								if ($key == 'created' || $key == 'modified' || $key == 'updated')
									// From http://snippets.dzone.com/posts/show/1455
									$val = gmdate('Y-m-d H:i:s', $val);
								
								//add the deviceId to the contact_id to create an anonomyous and
								//unique number in place of the real phone number:
								if ($key == 'contact_id')
									$val = $deviceid.$val;
								
								//deal with choice_ids, which uses HABTM
								if ($key == 'choice_ids')
								{
									$ids = explode(',', $val);
									if (!empty($ids))
										$toSave['Choice']['Choice'] = $ids;
								}
								else $toSave[$cake_name][$key] = $val;
							}
							//set the subject_id for all data based on deviceID
							$toSave[$cake_name]['subject_id'] = $subjectid;
							$this->$cake_name->create();
							if (!$this->$cake_name->save($toSave))
							{
								$result = false;
								$message = $this->$cake_name->validationErrors;
							}
						}
					}
				}
			}
		}
		//finally, set the results and possibly the error message for the view
		$this->set('result', $result);
		if ($result == false) $this->set('message', $message);
	}
	
	/**
	 * Used to send information about app crashes to the admin.
	 */
	function error()
	{
		$worked = false;
		$message = 'No POST data';
		if (array_key_exists('DEVICE_ID', $_POST) && !empty($_POST['DEVICE_ID']))
			$_POST['SUBJECT_ID'] = $this->getSubjectID($_POST['DEVICE_ID'], $message, $worked);
		if ($worked == false)
		{
			$this->set('result', $worked);
			$this->set('message', $message);
			return;
		}
		$this->set('result', true);
		$s = ' style="border:1px solid black;"';
		$body = "<html><body><table$s><tr$s><th$s colspan=\"2\">Survey Droid crash Report</th></tr>";
		foreach ($_POST as $key => $val)
		{
			$val = str_replace("\n", '<br />', $val);
			$body .= "<tr$s><td$s><strong>$key</strong></td><td$s>$val</td></tr>";
		}
		$body .= '</table></body></html>';
		$subject = 'Survey Droid exception received';
		$headers = "From: no-reply@survey-droid.org\r\n";
		$headers .= "Content-type: text/html\r\n"; 
		for ($i = 1; $i <= NUM_ADMIN_EMAILS; $i++)
		{
			$to = constant("ADMIN_EMAIL$i");
			mail($to, $subject, $body, $headers);
		}
	}
	
	/**
	 * Allows phones to get the salt string for phone number encryption.
	 */
	function salt($deviceid = NULL)
	{
		$subjectid = $this->getSubjectID($deviceid, $message, $worked);
		if ($worked == false)
		{
			$this->set('result', $worked);
			$this->set('message', $message);
			return;
		}
		$this->set('result', Security::hash($subjectid));
	}
	/* some notes:
	 * 
	 * convert an array to json => use js helper: $Js->value($array);
	 * 
	 * DATETIME <=> Unix timestamp => see http://snippets.dzone.com/posts/show/1455
	 */
}
?>