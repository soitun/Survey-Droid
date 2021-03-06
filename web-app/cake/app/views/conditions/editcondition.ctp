<?php
/*---------------------------------------------------------------------------*
 * views/conditions/editcondition.ctp                                        *
 *                                                                           *
 * Page to edit a condition.                                                 *
 *---------------------------------------------------------------------------*/

echo $this->Session->flash();

if (isset($result)) //if result is set, then the user has already submitted
{
	if ($result == true)
	{
		echo '<script>'.$this->Js->request(array
		(
			'action' => "showconditions/$branchid"),
			array('async' => true, 'update' => "#branch_conditions_$branchid")
		).'</script>';
		echo $this->Js->writeBuffer();
		return;
	}
	echo '<h3>There were errors</h3>';
}

//main form
echo $form->create('Condition', array(
	'url' => "editcondition/$id",
	'default' => false));
echo $form->input('question_id', array(
	'type' => 'text',
	'value' => $question_id));
echo $form->input('type', array
(
	'type' => 'select',
	'options' => array('just was', 'ever was', 'never has been'),
	'value' => $type
));
echo $form->input('choice_id', array('type' => 'text', 'value' => $choice_id));
echo $form->input('confirm', array('type' => 'hidden', 'value' => true));
echo $form->input('branch_id',
	array('type' => 'hidden', 'value' => $branchid));
echo $this->Js->submit('Edit', array(
	'action' => "editcondition/$id",
	'update' => "#condition_space_$id"));
echo $form->end();

//cancel button
echo $form->create('Condition', array('default' => false));
echo $form->input('cancel', array('type' => 'hidden', 'value' => true));
echo $form->input('confirm', array('type' => 'hidden', 'value' => false));
echo $form->input('branch_id',
	array('type' => 'hidden', 'value' => $branchid));
echo $this->Js->submit('Cancel', array(
	'action' => 'editcondition/$id',
	'update' => "#condition_space_$id"));
echo $form->end();

echo $this->Js->writeBuffer();
?>