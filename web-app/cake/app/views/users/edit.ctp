<?php
/*---------------------------------------------------------------------------*
 * views/users/edit.ctp                                                      *
 *                                                                           *
 * Allows admins to edit web users.                                          *
 *---------------------------------------------------------------------------*/
if ($session->read('Auth.User.admin'))
{
	echo $form->create('User', array('url' => '/users/edit/'.$user['id']));
	echo $form->input('username', array('default' => $user['username']) );
	echo $form->input('User.password_copy', array(
		'type' => 'password',
		'label' => 'Password'));
	echo $form->input('User.password_confirm', array(
		'type' => 'password',
		'label' => 'Confirm the password'));
	echo $form->input('User.email', array('default' => $user['email']));
	echo $form->input('User.first_name', array(
		'label' => 'First Name',
		'default' => $user['first_name']));
	echo $form->input('User.last_name', array(
		'label' => 'Last Name',
		'default' => $user['last_name']));
	echo "Make Admin";
	echo $form->checkbox('User.admin', array('checked' => $user['admin']));
	echo $form->hidden('User.id', array('value' => $user['id']));
	echo $form->end('Submit');
	
	if ($saved === true)
	{ 
		echo '<h3>User is changed.</h3>';
		echo '<h3>'.$html->link('Back', '/users/').'</h3>';
	}
}
?>

