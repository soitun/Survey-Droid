<?php
/*---------------------------------------------------------------------------*
 * app_controller.php                                                        *
 *                                                                           *
 * Holds application-wide controller-related code.                           *
 *---------------------------------------------------------------------------*/
/**
 * Application wide controller.  Loads application wide components;
 * specifically, it loads the Auth component to force all pages to require a
 * users to be loged in by default.
 * 
 * @author Austin Walker
 */
class AppController extends Controller
{
	//Load the Auth (ie authorization) and Session components for all
	//controllers; this causes all pages to default to requiring a user to log
	//in to see them.
    var $components = array('Auth', 'Session', 'Ssl');
    
	function beforeFilter()
	{
		//if the user has set the site to use ssl, force https connections
		if (SSL === true)
			$this->Ssl->force();
	}
}
?>