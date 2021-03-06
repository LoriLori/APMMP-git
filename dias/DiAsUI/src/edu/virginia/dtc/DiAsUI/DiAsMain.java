//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.DiAsUI;

import java.text.DecimalFormat;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Time;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Mode;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.SysMan.TempBasal;

public class DiAsMain extends Activity implements OnGestureListener {
	
    public static final String IO_TEST_TAG = "DiAsMainIO";
	public final String TAG = "DiAsMain";
	
	/************************************************************************************************************************/
	//  System Statics and Constants
	/************************************************************************************************************************/
	
	// DiAsService Commands
	private static final int DIAS_SERVICE_COMMAND_SET_EXERCISE_STATE = 4;
	private static final int DIAS_SERVICE_COMMAND_START_CLOSED_LOOP_CLICK = 11;
	private static final int DIAS_SERVICE_COMMAND_STOP_CLICK = 12;
	private static final int DIAS_SERVICE_COMMAND_START_OPEN_LOOP_CLICK = 13;
	private static final int DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK = 25;
	private static final int DIAS_SERVICE_COMMAND_START_SAFETY_CLICK = 26;

    // Confirmation dialogs
 	private static final int DIALOG_CLOSED_LOOP_NO_CGM = 254;	
 	private static final int DIALOG_CONFIRM_STOP = 256;
 	private static final int DIALOG_NEW_SUBJECT_CONFIRM = 257;	
 	private static final int DIALOG_PASSWORD = 258;
 	private static final int DIALOG_CONFIRM_CONFIG = 259;
 	private static final int DIALOG_CONFIRM_EXERCISE = 260;
 	private static final int DIALOG_CONFIRM_HYPO_TREATMENT = 261;
 	private static final int DIALOG_CONFIRM_CANCEL_TEMP_BASAL = 264;
 	
 	// Passworded button codes
 	private static int BUTTON_CURRENT;
 	private static final int BUTTON_NEW_SUBJECT = 0;
 	private static final int BUTTON_OPEN_LOOP = 1;
 	private static final int BUTTON_CLOSED_LOOP = 2;
 	private static final int BUTTON_HOME = 3;
 	private static final int BUTTON_SENSOR_ONLY = 4;
 	private static final int BUTTON_SAFETY = 5;
    
    // Activity Result IDs
    private static final int PLOTS = 2;
    private static final int SMBG = 4;
    private static final int TEMP_BASAL = 5;
    
 	/************************************************************************************************************************/
	//  System Variables
	/************************************************************************************************************************/
	
    //GLOBALS		******************************************************************************
    
  	private long SIM_TIME;
  	
  	private int DIAS_STATE;
  	private int BATTERY;
  	private double CGM_VALUE;
  	private int CGM_STATE;
  	private int PUMP_STATE;
  	private boolean EXERCISING;
  	private boolean ENABLE_IO;
  	
    
  	//VARIABLES		******************************************************************************
  	
	private final DiAsMain main = this;
	
	private GestureDetector gestureScanner;
	
	private BroadcastReceiver ServiceReceiver;				// Listens for information broadcasts from DiAsService
	private boolean ServiceReceiverIsRegistered = false;
	
	private BroadcastReceiver TickReceiver;					// Listens for Time Tick broadcasts from DiAsService
	private boolean TickReceiverIsRegistered = false;

	private boolean noCgmInClosedLoopAlarmPlaying;
	
	// No CGM Watchdog timer
	private Timer NoCgmWatchdogTimer;
	
	private TimerTask NoCgmWatchdogTimerTask;
	private static double NO_CGM_WATCHDOG_TIMEOUT_SECONDS = 300;
	
	// Used in dialogs
	private TextView textViewPassword;
	private EditText editTextPassword;
	
	// CGM data gap constants
	private boolean insulinSetupComplete = false;
	
	private boolean cgmBlinded = false;
	
	private int midFrameW, midFrameH;
	
	private SystemObserver sysObserver;
	
	/************************************************************************************************************************/
	//  Overridden Activity Functions
	/************************************************************************************************************************/
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	final String FUNC_TAG = "onCreate";
        super.onCreate(savedInstanceState);
       
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.mainlinear);
        
        gestureScanner = new GestureDetector(this);
        
        Debug.i(TAG, FUNC_TAG, "My UID="+android.os.Process.myUid());
             				
        ServiceReceiverIsRegistered = false;
        TickReceiverIsRegistered = false;
        noCgmInClosedLoopAlarmPlaying = false;
        
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		// This is the main method of UI update now, it listens to changes on the SYSTEM table
		sysObserver = new SystemObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.SYSTEM_URI, true, sysObserver);
		
		cgmBlinded = Params.getBoolean(getContentResolver(), "cgmBlinded", false);
		
        registerReceivers();
        
        if (!isMyServiceRunning()) 
   	    {
            initTrafficLights();	// If DiAsService not running Initialize the traffic lights
            initCGMMessage();		// Initialize the CGM message
        }
   	    else 
   	    	update();
   	    
        if(main.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
        	updateDiasMain();
        }
        
        final View tv = ((LinearLayout)this.findViewById(R.id.linearMid));
        ViewTreeObserver vto = tv.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

            public void onGlobalLayout() {
                ViewTreeObserver obs = tv.getViewTreeObserver();
                
                midFrameW = tv.getMeasuredWidth();
        		midFrameW += (0.07*midFrameW);
        		midFrameH = tv.getMeasuredHeight();
        		midFrameH += (0.07*midFrameH);
        		
        		Debug.i(TAG, FUNC_TAG, "MID FRAME WIDTH "+midFrameW+" MID FRAME HEIGHT "+midFrameH);
                
                obs.removeGlobalOnLayoutListener(this);
            }
        });
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        final String FUNC_TAG = "onConfigurationChanged";
        Debug.i(TAG, FUNC_TAG, "");
        
        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) 
        {
            Debug.i(TAG, FUNC_TAG, "Landscape");
            setContentView(R.layout.mainlinear);
            
            updateDiasMain();
		 	update();
        } 
        else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            Debug.i(TAG, FUNC_TAG, "Portrait");
        }
    }
    
    @Override
    protected void onStart() {
    	final String FUNC_TAG = "onStart";
        super.onStart();
        Debug.i(TAG, FUNC_TAG, "");
       
        update();
		
		Cursor c = getContentResolver().query(Biometrics.SUBJECT_DATA_URI, new String[]{"insulinSetupComplete"}, null, null, null);
		if (c.moveToFirst())
			insulinSetupComplete = c.getInt(c.getColumnIndex("insulinSetupComplete")) == 1;
		else
			insulinSetupComplete = false;
		
		c.close();
    }    
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	final String FUNC_TAG = "onActivityResult";
    	
    	updateDiasMain();
    }
    
    @Override
    protected void onStop() 
    {
    	final String FUNC_TAG = "onStop";
    	super.onStop();
    	Debug.i(TAG, FUNC_TAG, "");
    }    
    
    @Override
    protected void onDestroy() 
    {
    	final String FUNC_TAG = "onDestroy";
		super.onDestroy();
		Debug.i(TAG, FUNC_TAG, "");
		
		if (ServiceReceiverIsRegistered) 
		{
			unregisterReceiver(ServiceReceiver);
			ServiceReceiverIsRegistered = false;
		}
		
		if (TickReceiverIsRegistered) 
		{
			unregisterReceiver(TickReceiver);
			TickReceiverIsRegistered = false;
		}
		
		if(sysObserver != null)
			getContentResolver().unregisterContentObserver(sysObserver);
    }    
    
    @Override
    protected void onRestart() 
    {
    	final String FUNC_TAG = "onRestart";
        super.onRestart();
        Debug.i(TAG, FUNC_TAG, "");
    }
    
    @Override
    protected void onResume() 
    {
    	final String FUNC_TAG = "onResume";
        super.onResume();        
        
        if (!isMyServiceRunning()) 
        {
            initTrafficLights();	// If DiAsService not running Initialize the traffic lights
            initCGMMessage();		// Initialize the CGM message
        }
        else
            update();   
    }
    
    @Override
    protected void onPause() {
    	final String FUNC_TAG = "onPause";
        super.onPause();
        Debug.i(TAG, FUNC_TAG, "");
    }
    
    /************************************************************************************************************************/
	//  Gesture Listeners
	/************************************************************************************************************************/
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureScanner.onTouchEvent(event);
    }

    public boolean onDown(MotionEvent e) {
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return true;
    }

    public void onLongPress(MotionEvent e) {
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return true;
    }

    public void onShowPress(MotionEvent e) {
    }

    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    /************************************************************************************************************************/
	//  Context Menu, Dialog, and Key Stroke Functions
	/************************************************************************************************************************/
    
    protected Dialog onCreateDialog(int id) 
    {
        Dialog dialog;
        switch(id) {     
	        case DIALOG_CONFIRM_STOP:       
	            AlertDialog.Builder stopBuild = new AlertDialog.Builder(this);
	            String state = "";
	            switch (DIAS_STATE)
	            {
		            case State.DIAS_STATE_CLOSED_LOOP:
		        		state = "Closed Loop";
		        		break;
		            case State.DIAS_STATE_SAFETY_ONLY:
		            	state = "Safety";
		            	break;
		            case State.DIAS_STATE_SENSOR_ONLY:
		            	state = "Sensor";
		            	break;
		            case State.DIAS_STATE_OPEN_LOOP:
		            	state = "Pump";
		            	break;
	            }
	            stopBuild.setMessage("Do you want to stop " + state + " Mode now?")
	            // Change button order to match Negative-Positive conventions
	                   .setCancelable(false)
	                   .setPositiveButton("No", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   main.removeDialog(DIALOG_CONFIRM_STOP);
	                       }
	                   })
	                   .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   main.removeDialog(DIALOG_CONFIRM_STOP);
	                    	   stopConfirm();
	                       }
	                   });
	            dialog = stopBuild.create();
	            break;
	        case DIALOG_CONFIRM_CONFIG:       
	            AlertDialog.Builder confBuild = new AlertDialog.Builder(this);	     	
				String confirmstate="";
	            switch (BUTTON_CURRENT) {
					case BUTTON_OPEN_LOOP:
						confirmstate = "Pump";
						break;
					case BUTTON_CLOSED_LOOP:
						confirmstate = "Closed Loop";
						break;
					case BUTTON_SAFETY:
						confirmstate = "Safety";
						break;
					case BUTTON_SENSOR_ONLY:
						confirmstate = "Sensor";
						break;
				}
	            // Here we are deliberately changing Negative button label to "Yes" and Positive button label to "No"
	            // This is done because we need to build with SDK version 10 in order to have the Option button on screen
	            // but version 10 displays Positive-Negative where we need Negative-Positive to be consistent with the
	            // rest of the DiAs UI.
	            confBuild.setMessage("Do you want to start " + confirmstate + " Mode now?")
	                   .setCancelable(false)
	                   .setPositiveButton("No", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
      							dialog.dismiss();
	                       }
	                   })
	                   .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
								switch (BUTTON_CURRENT) {
									case BUTTON_OPEN_LOOP:
										openLoopConfirm();
										dialog.dismiss();
										break;
									case BUTTON_SENSOR_ONLY:
										sensorOnlyConfirm();
										dialog.dismiss();
										break;
									case BUTTON_CLOSED_LOOP:
										closedLoopConfirm();
										dialog.dismiss();
										break;
									case BUTTON_SAFETY:
										safetyConfirm();
										dialog.dismiss();
										break;
									}
	                       }
	                   });
	            dialog = confBuild.create();
	            dialog.show();
	            break;
	        case DIALOG_CONFIRM_EXERCISE:     
	        	if(Params.getInt(getContentResolver(), "exercise_detection_mode", 0) == 0)
    			{
		            AlertDialog.Builder exBuild = new AlertDialog.Builder(this);
		            state = "";
		            if (EXERCISING)
		            	state = "stopping";
		            else
		            	state = "starting to";
		            exBuild.setMessage("Are you " + state + " exercise now?")
		            // Change button order to match Negative-Positive conventions
		                   .setCancelable(false)
		                   .setPositiveButton("No", new DialogInterface.OnClickListener() {
		                       public void onClick(DialogInterface dialog, int id) {
		                    	   main.removeDialog(DIALOG_CONFIRM_EXERCISE);
		                       }
		                   })
		                   .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
		                       public void onClick(DialogInterface dialog, int id) {
		                    	   main.removeDialog(DIALOG_CONFIRM_EXERCISE);
		                    	   exerciseConfirm();
		                       }
		                   });
		            dialog = exBuild.create();
    			}
	        	else
	        	{
	        		Toast.makeText(this, "Automatic detection is enabled!", Toast.LENGTH_SHORT).show();
	        		dialog = null;
	        	}
	            break;
	        case DIALOG_CONFIRM_CANCEL_TEMP_BASAL:       
	            AlertDialog.Builder tbrBuild = new AlertDialog.Builder(this);
	            state = "";
	            if (temporaryBasalRateActive()) {
	            	tbrBuild.setMessage("Do you wish to resume normal basal delivery now?")
	            	// Change button order to match Negative-Positive conventions
	                   .setCancelable(false)
	                   .setPositiveButton("No", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   main.removeDialog(DIALOG_CONFIRM_CANCEL_TEMP_BASAL);
	                       }
	                   })
	                   .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   main.removeDialog(DIALOG_CONFIRM_CANCEL_TEMP_BASAL);
	                    	   cancelTemporaryBasalRate();
	                       }
	                   });
	            }
	            dialog = tbrBuild.create();
	            break;
	        case DIALOG_CONFIRM_HYPO_TREATMENT:
	        	AlertDialog.Builder htBuild = new AlertDialog.Builder(this);
	            htBuild.setMessage("Have you just treated yourself for hypoglycemia?")
	            // Change button order to match Negative-Positive conventions
	                   .setCancelable(false)
	                   .setPositiveButton("No", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   		main.removeDialog(DIALOG_CONFIRM_HYPO_TREATMENT);
	                    	   		hypoTreatmentConfirm(false);
	                       }
	                   })
	                   .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   		main.removeDialog(DIALOG_CONFIRM_HYPO_TREATMENT);
	                    	   		hypoTreatmentConfirm(true);
	                       }
	                   });
	            dialog = htBuild.create();
	            break;
	        case DIALOG_NEW_SUBJECT_CONFIRM:       
	            AlertDialog.Builder nsBuild = new AlertDialog.Builder(this);
	            nsBuild.setMessage("New Subject - Delete current database?")
	            // Change button order to match Negative-Positive conventions
	          		.setCancelable(false)
	          		.setPositiveButton("No", new DialogInterface.OnClickListener() {
	          			public void onClick(DialogInterface dialog, int id) {
	          				main.removeDialog(DIALOG_NEW_SUBJECT_CONFIRM);
	          			}
	          		})
	          		.setNegativeButton("Yes", new DialogInterface.OnClickListener() {
	          			public void onClick(DialogInterface dialog, int id) {  
	          				main.removeDialog(DIALOG_NEW_SUBJECT_CONFIRM);
	          				main.showDialog(DIALOG_PASSWORD);
	          			}
	          		});
	            dialog = nsBuild.create();
	            break;
	        case DIALOG_PASSWORD:
	    		dialog = new Dialog(this);
	    		dialog.setContentView(R.layout.passworddialog);
	    		String title = "";
				switch (BUTTON_CURRENT) {
					case BUTTON_NEW_SUBJECT:
						title = "New Subject";
						break;
					case BUTTON_OPEN_LOOP:
						title = "Start Open Loop";
						break;
					case BUTTON_CLOSED_LOOP:
						title = "Start Closed Loop";
						break;
					case BUTTON_SAFETY:
						title = "Start Safety";
						break;
					case BUTTON_SENSOR_ONLY:
						title = "Start Sensor Mode";
						break;
					case BUTTON_HOME:
						title = "Open Launch Screen";
						break;
				}
	    		dialog.setTitle(title);
	    	
	    		textViewPassword = (TextView) dialog.findViewById(R.id.textViewPassword);
	    		editTextPassword = (EditText) dialog.findViewById(R.id.editTextPassword);   
	    		Cursor c = getContentResolver().query(Biometrics.PASSWORD_URI, null, null, null, null);
				if (c.moveToLast()) {
					final String PASSWORD = c.getString(c.getColumnIndex("password"));
					final String BACKUP = Params.getString(getContentResolver(), "backup_password", null);
					
					textViewPassword.setText("    Enter password    ");
					((Button) dialog.findViewById(R.id.buttonPasswordOk)).setOnClickListener(new OnClickListener() {
						public void onClick(View v) {
							if (!editTextPassword.getText().toString().equals(PASSWORD) && !editTextPassword.getText().toString().equals(BACKUP)) {
								textViewPassword.setText("Invalid password, try again");
								editTextPassword.setText("");
							} 
							else {
								removeDialog(DIALOG_PASSWORD);
								switch (BUTTON_CURRENT) {
									case BUTTON_NEW_SUBJECT:
										newSubject();
										break;
									case BUTTON_OPEN_LOOP:
										openLoopConfirm();
										break;
									case BUTTON_SENSOR_ONLY:
										sensorOnlyConfirm();
										break;
									case BUTTON_CLOSED_LOOP:
										closedLoopConfirm();
										break;
									case BUTTON_SAFETY:
										safetyConfirm();
										break;
									case BUTTON_HOME:
										homeConfirm();
										break;
									}
								}
							}
						});
						((Button) dialog.findViewById(R.id.buttonPasswordCancel)).setOnClickListener(new OnClickListener() {
							public void onClick(View v) {
								main.removeDialog(DIALOG_PASSWORD);
							}
						});
					} else {
						textViewPassword.setText("No password found. Go to Setup to create password.");		
		        		editTextPassword.setEnabled(false);
						((Button) dialog.findViewById(R.id.buttonPasswordOk)).setOnClickListener(new OnClickListener() {
							public void onClick(View v) {
								main.removeDialog(DIALOG_PASSWORD);
							}
						});
						((Button) dialog.findViewById(R.id.buttonPasswordCancel)).setOnClickListener(new OnClickListener() {
							public void onClick(View v) {
								main.removeDialog(DIALOG_PASSWORD);
							}
						});
					}
					c.close();
					
	        	break;	        	
	        default:
	            dialog = null;
        }
        return dialog;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.clear();
    	boolean retCode = false;
        MenuInflater inflater = getMenuInflater();
        
        Debug.i(TAG, "onPrepareOptionsMenu", "Context Menu"+menu.size());
        
		//There isn't an alternative state since the plots are in their own activity now
		switch(DIAS_STATE)
		{
			case State.DIAS_STATE_STOPPED:
				inflater.inflate(R.menu.mainstopped, menu);
				retCode = true;
				break;
			case State.DIAS_STATE_OPEN_LOOP:
				inflater.inflate(R.menu.mainopen, menu);
				retCode = true;
				break;
			case State.DIAS_STATE_SENSOR_ONLY:
				inflater.inflate(R.menu.mainsensoronly, menu);
				retCode = true;
				break;
			case State.DIAS_STATE_CLOSED_LOOP:
			case State.DIAS_STATE_SAFETY_ONLY:
				inflater.inflate(R.menu.mainclosed, menu);
				retCode = true;
				break;
		}

        ioContextMenu(menu);
        
        return retCode;
    }
    
    private void ioContextMenu(Menu menu)
    {
    	for(int i = 0;i < menu.size();i++)
        	Debug.i(TAG, "IO_TEST", "Menu: "+menu.getItem(i).toString());
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {  			
    		case R.id.menuStoppedSaveDatabase:
    			main.getContentResolver().delete(Uri.parse("content://"+ Biometrics.PROVIDER_NAME + "/save"), null, null);
    			return true;
    		case R.id.menuStoppedNewSubject:
    			newSubjectClick(null);
    			return true;
    		case R.id.menuStoppedSubjectInformation:
    			goToSetupScreen(0);
    			return true;
    		case R.id.menuDeviceManager:
    			goToSetupScreen(5);
    			return true;
    		case R.id.menuAddBG:
    			addBgClick();	
    			return true;
    		case R.id.menuViewer:
    			Intent viewActivity = new Intent();
    	 		viewActivity.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.ViewerActivity"));
    	 		startActivity(viewActivity);
    			return true;
    		case R.id.menuParameters:
    			Intent controllerParams = new Intent();
    			String action = "edu.virginia.dtc.DiAsUI.parametersAction";
    			controllerParams.setAction(action);
    			sendBroadcast(controllerParams);
    			Debug.i(TAG, "onOptionItemSelected", "Params Button pressed, action: broadcast \""+ action +"\" intent");
    			//Toast.makeText(main, "Params Button pressed, action: broadcast \""+ action +"\" intent", Toast.LENGTH_SHORT).show();
    			return true;
    		default:
    			return super.onOptionsItemSelected(item);
        }
    }

    @Override 
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    //Handle the back button
		boolean retCode;
		switch (keyCode)
		{
			case KeyEvent.KEYCODE_BACK:
				updateDiasMain();
				retCode = true;
				break;
			case KeyEvent.KEYCODE_HOME:
				// If already on HOME screen then prompt to exit to system
				BUTTON_CURRENT = BUTTON_HOME;
				showDialog(DIALOG_PASSWORD);
				retCode = true;
				break;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				//Eat volume keys
				retCode = true;
				break;
			case KeyEvent.KEYCODE_APP_SWITCH:
				retCode = true;
				break;
			default:
				retCode =  super.onKeyDown(keyCode, event);
				break;
	    }
	    return retCode;
    }
    
    @Override 
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		boolean retCode;
		switch (keyCode)
		{
			case KeyEvent.KEYCODE_HOME:
				retCode = true;
				break;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				// Eat volume keys
				retCode = true;
				break;
			case KeyEvent.KEYCODE_APP_SWITCH:
				retCode = true;
				break;
			default:
				retCode =  super.onKeyDown(keyCode, event);
				break;
	    }
	    return retCode;
    }
    
    /************************************************************************************************************************/
	//  Main initialization (Broadcast Receivers etc.)
	/************************************************************************************************************************/
    
    private void registerReceivers() 
    {
		if (getIntent().getBooleanExtra("goToSetup", false))
			newSubject();
		
		editTextPassword = new EditText(this);

		// **************************************************************************************************************
     	// Register to receive status updates from DiAsService
        // **************************************************************************************************************
		
     	ServiceReceiver = new BroadcastReceiver() 
     	{
     		final String FUNC_TAG = "ServiceReceiver";
     		
            @Override
            public void onReceive(Context context, Intent intent) {        			
    			String action = intent.getAction();
    			Debug.i(TAG, FUNC_TAG, "ServiceReceiver -- "+action);  	
    			
    			// Handles commands for DiAsMain
    	        int command = intent.getIntExtra("DiAsMainCommand", 0);
    	        Debug.i(TAG, FUNC_TAG, "ServiceReceiver > command="+command);
    	        
    	        switch (command) 
    	        {
        	        default:
         				Bundle b = new Bundle();
         	    		b.putString("description", "DiAsMain > unexpected command: "+command);
         	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
    	        		break;
    	        }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("edu.virginia.dtc.intent.action.DIAS_SERVICE_UPDATE_STATUS");
        registerReceiver(ServiceReceiver, filter);
        ServiceReceiverIsRegistered = true;
      
        // Register to receive Supervisor Time Tick
        TickReceiver = new BroadcastReceiver() 
        {
        	final String FUNC_TAG = "TickReceiver";
        	
            @Override
            public void onReceive(Context context, Intent intent) 
            {
            	SIM_TIME = intent.getLongExtra("simulatedTime", -1);
        		Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
        		
            	if(c!=null)
            	{
            		if(c.moveToLast())
            		{
            			double cgmValue = c.getDouble(c.getColumnIndex("cgmValue"));
            			long cgmLastTime = c.getLong(c.getColumnIndex("cgmLastTime"));
            			
            			Debug.i(TAG, FUNC_TAG, "cgmValue: "+cgmValue+" time(in min): "+getTimeSeconds()/60+" cgmLastTime: "+cgmLastTime/60);
                		
                		if (cgmLastTime > 0) 
                		{
             		   		int minsAgo = (int)(getTimeSeconds() - cgmLastTime)/60;
             		   		
             		   		if (minsAgo < 0)
             		   			minsAgo = 0;
             		   	
             		   		Debug.i(TAG, FUNC_TAG, "Minutes ago: "+minsAgo);
             		   		
	             		   	String minsString = (minsAgo == 1) ? "min" : "mins";
	             		   	((TextView)findViewById(R.id.textViewCGMTime)).setText(minsAgo + " " + minsString + " ago");
	             		   	
	             		   	if (minsAgo == 0 || cgmValue < 39 || cgmValue > 401)
	                 		   	((TextView)findViewById(R.id.textViewCGMTime)).setVisibility(View.INVISIBLE);
	             		   	else
	                 		   	((TextView)findViewById(R.id.textViewCGMTime)).setVisibility(View.VISIBLE);
                		}
            			
            			c.close();
            		}
            		else
            			c.close();
            	}
            	update();
            }
        };
        IntentFilter filter1 = new IntentFilter();
        filter1.addAction("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK");
        registerReceiver(TickReceiver, filter1);    
        TickReceiverIsRegistered = true;
    }
    
    /************************************************************************************************************************/
	//  UI and System Update Functions
	/************************************************************************************************************************/
    
    class SystemObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public SystemObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "System Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) 
       {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "onChange";
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "System Observer: "+count);
    	   
    	   update();
       }		
    }
    
    private void update()
    {
    	final String FUNC_TAG = "update";
  	
    	Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
    	
    	if(c!=null)
    	{
    		if(c.moveToLast())
    		{
    			Debug.i(TAG, FUNC_TAG, "Updating UI...");	
    			updateUI(c);
    			c.close();
    		}
    		else
    		{
    			c.close();
    			return;
    		}
    	}
    	else
    		return;
    }
    
    private void updateUI(Cursor c)
    {
    	final String FUNC_TAG = "updateUI";
    	
    	long start = System.currentTimeMillis();
    	long stop;
    	
    	long time = c.getLong(c.getColumnIndex("time"));
    	long sysTime = c.getLong(c.getColumnIndex("sysTime"));
    	
    	int diasState = c.getInt(c.getColumnIndex("diasState"));
    	int battery = c.getInt(c.getColumnIndex("battery"));
    	
    	boolean safetyMode = (c.getInt(c.getColumnIndex("safetyMode"))==1) ? true : false;
    	
    	double cgmValue = c.getDouble(c.getColumnIndex("cgmValue"));
    	int cgmTrend = c.getInt(c.getColumnIndex("cgmTrend"));
    	long cgmLastTime = c.getLong(c.getColumnIndex("cgmLastTime"));
    	int cgmState = c.getInt(c.getColumnIndex("cgmState"));
    	String cgmStatus = c.getString(c.getColumnIndex("cgmStatus"));
    	
    	double pumpLastBolus = c.getDouble(c.getColumnIndex("pumpLastBolus"));
    	long pumpLastBolusTime = c.getLong(c.getColumnIndex("pumpLastBolusTime"));
    	int pumpState = c.getInt(c.getColumnIndex("pumpState"));
    	String pumpStatus = c.getString(c.getColumnIndex("pumpStatus"));
    	
    	int hypoLight = c.getInt(c.getColumnIndex("hypoLight"));
    	int hyperLight = c.getInt(c.getColumnIndex("hyperLight"));
    	
    	double apcBolus = c.getDouble(c.getColumnIndex("apcBolus"));
    	int apcStatus = c.getInt(c.getColumnIndex("apcStatus"));
    	int apcType = c.getInt(c.getColumnIndex("apcType"));
    	String apcString = c.getString(c.getColumnIndex("apcString"));
    	
    	boolean exercising = (c.getInt(c.getColumnIndex("exercising"))==1) ? true : false;
    	boolean alarmNoCgm = (c.getInt(c.getColumnIndex("alarmNoCgm"))==1) ? true : false;
    	boolean alarmHypo = (c.getInt(c.getColumnIndex("alarmHypo"))==1) ? true : false;
    	
    	if(Params.getBoolean(getContentResolver(), "enableIO", false))
    	{
    		final String IO_TAG = "IO_TEST";
	    	Debug.i(TAG, IO_TAG, "time: "+time+" sysTime: "+sysTime+" diasState: "+diasState+" battery: "+battery+" safetyMode: "+safetyMode+" enableIOTest: "+Params.getBoolean(getContentResolver(), "enableIO", false));
	    	Debug.i(TAG, IO_TAG, "cgmValue: "+cgmValue+" cgmTrend: "+cgmTrend+" cgmLastTime: "+cgmLastTime+" cgmState: "+cgmState+" cgmStatus: "+cgmStatus);
	    	Debug.i(TAG, IO_TAG, "pumpLastBolus: "+pumpLastBolus+" pumpLastBolusTime: "+pumpLastBolusTime+" pumpState: "+pumpState+" pumpStatus: "+pumpStatus);
	    	Debug.i(TAG, IO_TAG, "hypoLight: "+hypoLight+" hyperLight: "+hyperLight);
	    	Debug.i(TAG, IO_TAG, "apcBolus: "+apcBolus+" apcStatus: "+apcStatus+" apcType: "+apcType+" apcString: "+apcString);
	    	Debug.i(TAG, IO_TAG, "exercising: "+exercising+" alarmNoCgm: "+alarmNoCgm+" alarmHypo: "+alarmHypo);
	    	Debug.i(TAG, IO_TAG, "----------------------------------------------------------------------------------------------------------------------------------");
    	}
    	
    	//Call functions to update the UI each time there is a change in the SYSTEM table
    	if(this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
    	{
	    	updateLastBolus(diasState, pumpLastBolus, pumpLastBolusTime);
	    	
	    	updateTrafficLights(diasState, hypoLight, hyperLight, alarmHypo);
	    	
	    	updateCgm(cgmValue, cgmTrend, cgmLastTime, cgmState);
    	}
    	else
    		Debug.w(TAG, FUNC_TAG, "The activity is in portrait mode...");
    	
    	//Update constants for system
    	DIAS_STATE = diasState;
    	CGM_VALUE = cgmValue;
    	PUMP_STATE = pumpState;
    	CGM_STATE = cgmState;
    	BATTERY = battery;
    	EXERCISING = exercising;
    	
    	//Update the DiAs UI state (show/hide objects)
    	if(this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
    	{
    		updateDiasMain();
    	}
    	else
    		Debug.w(TAG, FUNC_TAG, "The activity is in portrait mode...");
    	
    	//Custom Flex Button Icons
    	updateFlexButtons();
    	
    	stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private void updateFlexButtons()
    {
    	Drawable icon = getResources().getDrawable(R.drawable.meal_button_normal);
        try 
        {
        	icon = getPackageManager().getApplicationIcon("edu.virginia.dtc.MCMservice");
        } 
        catch (NameNotFoundException e) 
        {
        	e.printStackTrace();
        }
         
        Button b = (Button)this.findViewById(R.id.buttonMeal);
        if(icon != null)
        	b.setBackground(icon);
    }
    
    private void updateDiasMain() 
    {
		final String FUNC_TAG = "updateDiasMain";
		long start = System.currentTimeMillis();
    	long stop;

		Debug.i(TAG, FUNC_TAG, "Updating Dias Main...");
		
		mainGroupShow();    
		
		stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private void updateLastBolus(int diasState, double pumpLastBolus, long pumpLastBolusTime)
    {
    	final String FUNC_TAG = "updateLastBolus";
    	long start = System.currentTimeMillis();
    	long stop;
    	
    	TextView textViewBolus = (TextView)this.findViewById(R.id.textViewBolusInfo);
  	   	if (diasState == State.DIAS_STATE_SENSOR_ONLY || cgmBlinded) 
  	   	{
  	   		textViewBolus.setVisibility(TextView.INVISIBLE);
  	   	}
  	   	else 
  	   	{
  	   		textViewBolus.setVisibility(TextView.VISIBLE);
  	   		
  	   		Time time = new Time();
  	   		time.set(pumpLastBolusTime*1000);
  	   		
  	   		if ((int)pumpLastBolus == -1)
  	   			textViewBolus.setText(" Last bolus: --");
  	   		else
  	   		{
  	   			DecimalFormat bolusFormat = new DecimalFormat();
  	   			bolusFormat.setMaximumFractionDigits(2);
  	   			bolusFormat.setMinimumFractionDigits(2);
  	   			textViewBolus.setText(" Last bolus: " + bolusFormat.format(pumpLastBolus) + " U at " + time.format("%I:%M %p").toUpperCase());
  	   		}
  	   	}
  	   	
  	   	stop = System.currentTimeMillis();
  	   	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private void updateTrafficLights(int diasState, int hypoLight, int hyperLight, boolean alarmHypo)
    {
    	final String FUNC_TAG = "updateTrafficLights";
    	long start = System.currentTimeMillis();
    	long stop;
    	
    	final int RED_FRAME = Color.rgb(255, 25, 0);
		
		ImageView hypoLightRed = (ImageView)this.findViewById(R.id.hypoLightRed);
		ImageView hypoLightYellow = (ImageView)this.findViewById(R.id.hypoLightYellow);
		ImageView hypoLightGreen = (ImageView)this.findViewById(R.id.hypoLightGreen);
		ImageView hypoLightBorder = (ImageView)this.findViewById(R.id.hypoLightBorder);
		ImageView hyperLightRed = (ImageView)this.findViewById(R.id.hyperLightRed);
		ImageView hyperLightYellow = (ImageView)this.findViewById(R.id.hyperLightYellow);
		ImageView hyperLightGreen = (ImageView)this.findViewById(R.id.hyperLightGreen);
		ImageView hyperLightBorder = (ImageView)this.findViewById(R.id.hyperLightBorder);
		ImageView hypoTreatButtonBorder = (ImageView)this.findViewById(R.id.android_hypo_treatment_button_border);
		
		TextView hypoText = (TextView) findViewById(R.id.textViewHypo);
		TextView hyperText = (TextView) findViewById(R.id.textViewHyper);

		// Traffic lights are only illuminated when DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP
		makeTrafficLightsInvisible();
		
		hypoText.setTextColor(Color.rgb(0xBB, 0xBB, 0xBB)); // #BBBBBB
		hypoLightBorder.clearColorFilter();
		
		hyperText.setTextColor(Color.rgb(0xBB, 0xBB, 0xBB)); // #BBBBBB
		hyperLightBorder.clearColorFilter();
		hypoTreatButtonBorder.setVisibility(Button.INVISIBLE);
		
		if (diasState == State.DIAS_STATE_CLOSED_LOOP || diasState == State.DIAS_STATE_OPEN_LOOP || diasState == State.DIAS_STATE_SAFETY_ONLY || diasState == State.DIAS_STATE_SENSOR_ONLY) 
		{
			switch(hypoLight)
			{
				case Safety.RED_LIGHT:
					hypoLightRed.setVisibility(View.VISIBLE);
					if (alarmHypo) 
					{
						// Remove Dialog boxes that might be in the way
						removeDialog(DIALOG_PASSWORD);
						removeDialog(DIALOG_CONFIRM_STOP);
						removeDialog(DIALOG_CONFIRM_EXERCISE);
						removeDialog(DIALOG_CONFIRM_HYPO_TREATMENT);
					}
					
					// Make Hypo frame glow red to be super obvious that there is a problem
					hypoText.setTextColor(RED_FRAME);
					hypoLightBorder.setColorFilter(RED_FRAME);
					hypoTreatButtonBorder.setVisibility(Button.VISIBLE);
					hypoTreatButtonBorder.setColorFilter(RED_FRAME);
					break;
				case Safety.YELLOW_LIGHT:
					hypoLightYellow.setVisibility(View.VISIBLE);
					break;
				case Safety.GREEN_LIGHT:
					hypoLightGreen.setVisibility(View.VISIBLE);
					break;
				default:
					hypoLightBorder.setVisibility(View.VISIBLE);
					Debug.i(TAG, FUNC_TAG, "Invalid hypolight="+hypoLight);
					break;
			}
			
			switch(hyperLight)
			{
				case Safety.RED_LIGHT:
					hyperLightRed.setVisibility(View.VISIBLE);
					
					// Make hyper frame glow red to be super obvious that there is a problem
					hyperText.setTextColor(RED_FRAME);
					hyperLightBorder.setColorFilter(RED_FRAME);
					break;
				case Safety.YELLOW_LIGHT:
					hyperLightYellow.setVisibility(View.VISIBLE);
					break;
				case Safety.GREEN_LIGHT:
					hyperLightGreen.setVisibility(View.VISIBLE);
					break;
				default:
					hyperLightBorder.setVisibility(View.VISIBLE);
					Debug.i(TAG, FUNC_TAG, "Invalid hyperlight="+hyperLight);
					break;
			}
		}
		
		stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private void updateCgm(double cgmValue, int cgmTrend, long cgmLastTime, int cgmState)
    {
    	final String FUNC_TAG = "updateCgm";
    	long start = System.currentTimeMillis();
    	long stop;
    	int blood_glucose_display_units = Params.getInt(getContentResolver(), "blood_glucose_display_units", CGM.BG_UNITS_MG_PER_DL);
    	
    	double cgmValueInSelectedUnits = cgmValue;
    	String unit_string = new String(" mg/dl");
    	if (blood_glucose_display_units == CGM.BG_UNITS_MMOL_PER_L) {
    		cgmValueInSelectedUnits = cgmValueInSelectedUnits/CGM.MGDL_PER_MMOLL;
    		unit_string = " mmol/L";
    	}

		TextView textViewCGM = (TextView)this.findViewById(R.id.textViewCGM);
		TextView textViewCGMTime = (TextView)this.findViewById(R.id.textViewCGMTime);
		
		DecimalFormat decimalFormat = new DecimalFormat();
    	if (blood_glucose_display_units == CGM.BG_UNITS_MMOL_PER_L) {
    		decimalFormat.setMinimumFractionDigits(1);
    		decimalFormat.setMaximumFractionDigits(1);
    	}
    	else {
    		decimalFormat.setMinimumFractionDigits(0);
    		decimalFormat.setMaximumFractionDigits(0);
    	}
		String CGMString = decimalFormat.format(cgmValueInSelectedUnits);
		
		int minsAgo = (int)((getTimeSeconds() - cgmLastTime)/60);
		if (minsAgo < 0)
			minsAgo = 0;
		String minsString = (minsAgo == 1) ? "min" : "mins";

		switch(cgmState)
		{
			case CGM.CGM_NORMAL:
				// We no longer enforce hard limits here since the DiAs Service 
				// will already do that and is capable of adjusting based on device parameters
				textViewCGM.setText(CGMString+unit_string);
				textViewCGMTime.setText((minsAgo == 0) ? "" : (minsAgo + " " + minsString + " ago"));
				Debug.i(TAG, FUNC_TAG, "CGM State Normal: "+minsAgo+" min old");
				break;
			case CGM.CGM_DATA_ERROR:
				textViewCGM.setText("Data Error");
				break;
			case CGM.CGM_NOT_ACTIVE:
				textViewCGM.setText("CGM Inactive");
				break;
			case CGM.CGM_NONE:
				textViewCGM.setText("");
				break;
			case CGM.CGM_NOISE:
				textViewCGM.setText("CGM Noise");
				break;
			case CGM.CGM_WARMUP:
				textViewCGM.setText("Warm-Up");
				break;
			case CGM.CGM_CALIBRATION_NEEDED:
				textViewCGM.setText("Calibrate");
				break;
			case CGM.CGM_DUAL_CALIBRATION_NEEDED:
				textViewCGM.setText("Calibrate");
				break;
			case CGM.CGM_CAL_LOW:
				textViewCGM.setText("Cal Low");
				break;
			case CGM.CGM_CAL_HIGH:
				textViewCGM.setText("Cal High");
				break;
			case CGM.CGM_SENSOR_FAILED:
				textViewCGM.setText("CGM Sensor Failed");
				break;
		}
		if (cgmBlinded) {
			textViewCGM.setText("CGM Blinded");
		}
		
		ImageView imageViewArrow = (ImageView)this.findViewById(R.id.imageViewArrow);

		if(cgmReady() && !cgmBlinded)		//Only show the trend if a value is being shown
		{
			switch (cgmTrend)
			{
	 			case 2:
	 				Debug.i(TAG, FUNC_TAG, "Trend Up");
	 				imageViewArrow.setBackgroundResource(R.drawable.arrow_2);
	 				imageViewArrow.setVisibility(ImageView.VISIBLE);
	 				textViewCGMTime.setVisibility(TextView.VISIBLE);
	 				break;
	 			case 1:
	 				Debug.i(TAG, FUNC_TAG, "Trend Up-Right");
	 				imageViewArrow.setBackgroundResource(R.drawable.arrow_1);
	 				imageViewArrow.setVisibility(ImageView.VISIBLE);
	 				textViewCGMTime.setVisibility(TextView.VISIBLE);
	 				break;
	 			case 0:
	 				Debug.i(TAG, FUNC_TAG, "Trend Flat");
	 				imageViewArrow.setBackgroundResource(R.drawable.arrow_0);
	 				imageViewArrow.setVisibility(ImageView.VISIBLE);
	 				textViewCGMTime.setVisibility(TextView.VISIBLE);
	 				break;
	 			case -1:
	 				Debug.i(TAG, FUNC_TAG, "Trend Down-Right");
	 				imageViewArrow.setBackgroundResource(R.drawable.arrow_m1);
	 				imageViewArrow.setVisibility(ImageView.VISIBLE);
	 				textViewCGMTime.setVisibility(TextView.VISIBLE);
	 				break;
	 			case -2:
	 				Debug.i(TAG, FUNC_TAG, "Trend Down");
	 				imageViewArrow.setBackgroundResource(R.drawable.arrow_m2);
	 				imageViewArrow.setVisibility(ImageView.VISIBLE);
	 				textViewCGMTime.setVisibility(TextView.VISIBLE);
	 				break;
	 			case 5:
	 				Debug.i(TAG, FUNC_TAG, "No Trend");
	 				imageViewArrow.setVisibility(ImageView.INVISIBLE);
	 				break;
	 			default:
	 				Debug.i(TAG, FUNC_TAG, "Unknown Trend");
	 				imageViewArrow.setVisibility(ImageView.INVISIBLE);
	 				textViewCGMTime.setVisibility(TextView.INVISIBLE);
			}
		}
		
		stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private boolean pumpReadyNoReco()
    {
    	Debug.i(TAG, "pumpReady", "PUMP_STATE: "+PUMP_STATE);
    	
    	switch(PUMP_STATE)
	    {
    		case Pump.CONNECTED:
	    	case Pump.CONNECTED_LOW_RESV:
    			return true;
	    	default:
	    		return false;
    	}
    }
    
    private boolean cgmReady()
    {
    	Debug.i(TAG, "cgmReady", "CGM_STATE: "+CGM_STATE);
    	
    	switch(CGM_STATE)
    	{
	    	case CGM.CGM_NORMAL:
	    	case CGM.CGM_CAL_HIGH:
	    	case CGM.CGM_CAL_LOW:
	    	case CGM.CGM_CALIBRATION_NEEDED:
	    	case CGM.CGM_DUAL_CALIBRATION_NEEDED:
	    		return true;
    		default:
    			return false;
    	}
    }
    
    /************************************************************************************************************************/
	//  UI Helper Functions
	/************************************************************************************************************************/
       
	private void initTrafficLights() 
	{
		makeTrafficLightsInvisible();
		ImageView hypoLightOff = (ImageView)this.findViewById(R.id.hypoLightOff);
		ImageView hyperLightOff = (ImageView)this.findViewById(R.id.hyperLightOff);
		hypoLightOff.setVisibility(View.VISIBLE);
		hyperLightOff.setVisibility(View.VISIBLE);
	}
	
    private void makeTrafficLightsInvisible() 
    {
		ImageView hypoLightRed = (ImageView)this.findViewById(R.id.hypoLightRed);
		ImageView hypoLightYellow = (ImageView)this.findViewById(R.id.hypoLightYellow);
		ImageView hypoLightGreen = (ImageView)this.findViewById(R.id.hypoLightGreen);
		ImageView hyperLightRed = (ImageView)this.findViewById(R.id.hyperLightRed);
		ImageView hyperLightYellow = (ImageView)this.findViewById(R.id.hyperLightYellow);
		ImageView hyperLightGreen = (ImageView)this.findViewById(R.id.hyperLightGreen);
		hypoLightRed.setVisibility(View.INVISIBLE);
		hypoLightYellow.setVisibility(View.INVISIBLE);
		hypoLightGreen.setVisibility(View.INVISIBLE);
		hyperLightRed.setVisibility(View.INVISIBLE);
		hyperLightYellow.setVisibility(View.INVISIBLE);
		hyperLightGreen.setVisibility(View.INVISIBLE);
     }
    
    private void initCGMMessage() 
    {
 	   TextView textViewCGMMessage = (TextView)this.findViewById(R.id.textViewCGM);		   
       textViewCGMMessage.setText("");
    }
   
   	/************************************************************************************************************************/
    //  Main DiAs UI Buttons (Loop modes, plots, treatment, exercise, etc.)
    /************************************************************************************************************************/
   
   	public void stopClick(View view) 
   	{
   		showDialog(DIALOG_CONFIRM_STOP);
	}
   	
   	public void openLoopClick(View view){
	 	BUTTON_CURRENT = BUTTON_OPEN_LOOP;
	 	onCreateDialog(DIALOG_CONFIRM_CONFIG);
   	}
   	
   	public void closedLoopClick(View view){
	 	BUTTON_CURRENT = BUTTON_CLOSED_LOOP;
	 	onCreateDialog(DIALOG_CONFIRM_CONFIG);
   	}
   	   	
   	public void safetyClick(View view){
	 	BUTTON_CURRENT = BUTTON_SAFETY;
	 	onCreateDialog(DIALOG_CONFIRM_CONFIG);
   	}
   	   	
   	public void sensorOnlyClick(View view){
	 	BUTTON_CURRENT = BUTTON_SENSOR_ONLY;
	 	onCreateDialog(DIALOG_CONFIRM_CONFIG);
   	}
   	   	
   	public void newSubjectClick(View view){
	 	BUTTON_CURRENT = BUTTON_NEW_SUBJECT;
   		showDialog(DIALOG_NEW_SUBJECT_CONFIRM);
   	}
 
    public void hypoTreatmentClick(View view) {
    	final String FUNC_TAG = "hypoTreatmentClick";
    	
 	   Debug.i(TAG, FUNC_TAG, "tapclink1");
 	   Bundle b = new Bundle();
 	   b.putString("description", "Hypo treatment first button pressed");
 	   Event.addEvent(getApplicationContext(), Event.EVENT_UI_HYPO_BUTTON_PRESSED, Event.makeJsonString(b), Event.SET_CUSTOM);
    }
    
    public void addBgClick() 
    {
    	Intent smbgAct = new Intent();
    	smbgAct.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.SmbgActivity"));
    	smbgAct.putExtra("height", midFrameH);
    	smbgAct.putExtra("width", midFrameW);
    	smbgAct.putExtra("state", DIAS_STATE);
    	smbgAct.putExtra("simulatedTime", getTimeSeconds());
    	smbgAct.putExtra("standaloneInstalled", standaloneDriverAvailable());
 		startActivityForResult(smbgAct, SMBG);
    }
    
    public void temporaryBasalStartClick(View view)
    {
    	final String FUNC_TAG = "temporaryBasalStartClick";
 	   	Debug.i(TAG, FUNC_TAG, "tapclink1");
 	   	
 	   	int tbrAvailable = Params.getInt(getContentResolver(), "temporaryBasalRateEnabled", 0);
 	   	
 	   	if (temporaryBasalRateActive()) {
	   		Bundle b = new Bundle();
			b.putString("description", FUNC_TAG+" while temporaryBasalRateActive==true");
			Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
 	   	}
 	   	else {
 	    	if (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP && (tbrAvailable == TempBasal.MODE_AVAILABLE_PUMP || tbrAvailable == TempBasal.MODE_NAVAILABLE_PUMP_AND_CL)) {
 		 		Intent tbIntent = new Intent();
 		 		tbIntent.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.TempBasalActivity"));
 		 		startActivityForResult(tbIntent, TEMP_BASAL);
 	    	}
 	    	else if ((DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) 
 	    			&& (tbrAvailable == TempBasal.MODE_AVAILABLE_CL || tbrAvailable == TempBasal.MODE_NAVAILABLE_PUMP_AND_CL)) {
 			    Intent intentBroadcast = new Intent("edu.virginia.dtc.intent.action.TEMP_BASAL");
 			    intentBroadcast.putExtra("command", TempBasal.TEMP_BASAL_START);
 		        sendBroadcast(intentBroadcast);
 	    	}
 	    	else {
 		   		Bundle b = new Bundle();
 				b.putString("description", FUNC_TAG+" while invalid mode or temp basal not enabled");
 				Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
 	    	}
 	   	}
    }
    
    public void temporaryBasalCancelClick(View view)
    {
    	final String FUNC_TAG = "temporaryBasalCancelClick";
 	   	Debug.i(TAG, FUNC_TAG, "tapclink1");
 	   	
 	    int tbrAvailable = Params.getInt(getContentResolver(), "temporaryBasalRateEnabled", 0);
 	   	
 	    if (temporaryBasalRateActive()) {
 	   		if (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP) {
 	 	 	   	showDialog(DIALOG_CONFIRM_CANCEL_TEMP_BASAL);
 	   		}
 	   		else if ((DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) 
 	   				&& (tbrAvailable == TempBasal.MODE_AVAILABLE_CL || tbrAvailable == TempBasal.MODE_NAVAILABLE_PUMP_AND_CL)) //&& temporaryBasalRateActivityAvailable()) 
 	   		{
 		        showDialog(DIALOG_CONFIRM_CANCEL_TEMP_BASAL); // cancel TBR in DiAs Ui
 	   		}
 	   		else {
 		   		Bundle b = new Bundle();
 				b.putString("description", FUNC_TAG+" while invalid mode or temp basal not enabled");
 				Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
 	   		}
 	   	}
 	   	else {
	   		Bundle b = new Bundle();
			b.putString("description", FUNC_TAG+" while temporaryBasalRateActive==false");
			Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
 	   	}
    }
    
    public void exerciseClick(View view) {
    	final String FUNC_TAG = "exerciseClick";
 	   	Debug.i(TAG, FUNC_TAG, "tapclink1");
 	   	showDialog(DIALOG_CONFIRM_EXERCISE);
    }
    
   	public void mealClick(View view) {
   		final String FUNC_TAG = "mealClick";
	 	
	 	if(PUMP_STATE == Pump.CONNECTED || PUMP_STATE == Pump.CONNECTED_LOW_RESV)
	 	{
		 		Debug.i(TAG, FUNC_TAG, "Starting MealActivity!");
		 		
		 		Intent meal = new Intent("DiAs.MealActivity");
		 		sendBroadcast(meal);
 		}
 		else
 		{
 			Debug.e(TAG, FUNC_TAG, "Pump is not connected, cannot launch Meal Activity!");
 			Toast.makeText(main, "Sorry, the pump is disconnected and a meal bolus cannot be processed!", Toast.LENGTH_LONG).show();
 		}
   	}
   	    
   	public void plotsClick(View view) 
    {   		
   		final String FUNC_TAG = "plotsClick";
    	 
   		if (cgmBlinded) {
   			Toast.makeText(getApplicationContext(), "Plots unavailable when system is Blinded", Toast.LENGTH_SHORT).show();
   		}
   		else {
	    	Intent plotsDisplay = new Intent();
	 		plotsDisplay.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.PlotsActivity"));
	    	plotsDisplay.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
	 		plotsDisplay.putExtra("height", midFrameH);
	 		plotsDisplay.putExtra("width", midFrameW);
	 		plotsDisplay.putExtra("state", DIAS_STATE);
	 		plotsDisplay.putExtra("simTime", getTimeSeconds());
	 		startActivityForResult(plotsDisplay, PLOTS);
   		}
    	 
 		Debug.i(TAG, FUNC_TAG, "after plotsClick");
     }
   		
   	private void checkVisible(Button b, int v)
   	{
   		if(b.getVisibility() == v)
   			return;
   		else
   			b.setVisibility(v);
   	}
   	   	
   	private void checkVisible(FrameLayout f, int v)
   	{
   		if(f.getVisibility() == v)
   			return;
   		else
   			f.setVisibility(v);
   	}
   	   	
   	private void checkVisible(LinearLayout l, int v)
   	{
   		if(l.getVisibility() == v)
   			return;
   		else
   			l.setVisibility(v);
   	}
   	   	
   	private void mainGroupShow() 
   	{
   	   final String FUNC_TAG = "mainGroupShow";
   	   
	   long start = System.currentTimeMillis();
	   long stop;
   		
	   FrameLayout mainButtonsHigh = (FrameLayout)findViewById(R.id.frameMidHighButtons);
	   FrameLayout mainButtonsLow = (FrameLayout)findViewById(R.id.frameMidLowButtons);
	   FrameLayout frame1 = (FrameLayout) findViewById(R.id.frameExerciseNewsubject);
	   FrameLayout frame2 = (FrameLayout) findViewById(R.id.frameHypoOpenloop);
	   FrameLayout frame3 = (FrameLayout) findViewById(R.id.frameTemporaryBasal);
//	   FrameLayout frame3 = (FrameLayout) findViewById(R.id.frameCalibrationClosedloop);
	   FrameLayout frame4 = (FrameLayout) findViewById(R.id.frameStopStart);
	   
	   LinearLayout infoScreen = (LinearLayout)findViewById(R.id.linearInfoScreen);
	   LinearLayout infoCGMStatus = (LinearLayout)findViewById(R.id.linearMidInfoCGM);
	   LinearLayout infoExtra = (LinearLayout)findViewById(R.id.linearMidInfo);
	   
 	   // Find buttons
 	   Button buttonExercise = (Button)this.findViewById(R.id.buttonExercise);
// 	   Button buttonCalibration = (Button)this.findViewById(R.id.buttonCalibration);
 	   Button buttonStartTemporaryBasal = (Button)this.findViewById(R.id.buttonStartTemporaryBasal);
 	   Button buttonCancelTemporaryBasal = (Button)this.findViewById(R.id.buttonCancelTemporaryBasal);
 	   Button buttonHypoTreatment = (Button)this.findViewById(R.id.buttonHypoTreatment);
 	   Button buttonStop = (Button)this.findViewById(R.id.buttonStop);
 	   Button buttonMeal = (Button)this.findViewById(R.id.buttonMeal);
 	   Button buttonPlots = (Button)this.findViewById(R.id.buttonPlots);
 	   Button buttonSensorOnly = (Button) findViewById(R.id.buttonSensorOnly);
 	   Button buttonOpenLoop = (Button) findViewById(R.id.buttonOpenLoop);
 	   Button buttonSafety = (Button) findViewById(R.id.buttonSafety);	  
 	   Button buttonClosedLoop = (Button) findViewById(R.id.buttonClosedLoop);	  
 	   
 	   checkVisible(mainButtonsHigh, FrameLayout.VISIBLE);
 	   checkVisible(mainButtonsLow, FrameLayout.VISIBLE);
 	   
 	   buttonSensorOnly.setVisibility(Button.INVISIBLE);
 	   buttonOpenLoop.setVisibility(Button.INVISIBLE);
 	   buttonClosedLoop.setVisibility(Button.INVISIBLE);
 	   
 	   // Get the offset in minutes into the current day in the current time zone (based on smartphone time zone setting)
 	   TimeZone tz = TimeZone.getDefault();
 	   int UTC_offset_secs = tz.getOffset(getTimeSeconds()*1000)/1000;
 	   int timeNowMins = (int)((getTimeSeconds()+UTC_offset_secs)/60)%1440;    		
 	   
 	   //TODO: Not only check Allowed Mode but also Controller Status / Night Profile Activity
 	   boolean PumpModeEnabled = Mode.isPumpModeAvailable(getContentResolver());
 	   boolean SafetyModeEnabled = Mode.isSafetyModeAvailable(getContentResolver(), timeNowMins);
 	   boolean ClosedLoopEnabled = Mode.isClosedLoopAvailable(getContentResolver(), timeNowMins);
 	   
 	   Debug.i(TAG, FUNC_TAG, "CLenable: "+ClosedLoopEnabled+" OLenable: "+PumpModeEnabled+" mode:"+Mode.getMode(getContentResolver()));
 	   
 	  int tbrAvailable = Params.getInt(getContentResolver(), "temporaryBasalRateEnabled", 0);
 	   
 	   switch (DIAS_STATE) 
 	   {
 	   		case State.DIAS_STATE_STOPPED:
 	   			checkVisible(mainButtonsHigh, FrameLayout.VISIBLE);
 	   			checkVisible(frame1, FrameLayout.INVISIBLE);
 	   			checkVisible(frame2, FrameLayout.VISIBLE);
 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	   			checkVisible(frame4, FrameLayout.INVISIBLE); 	   			
 	   			checkVisible(infoScreen, LinearLayout.VISIBLE);
 	   			checkVisible(infoCGMStatus, LinearLayout.VISIBLE);
 	   			checkVisible(infoExtra, LinearLayout.INVISIBLE);
 	   			
 	   			checkVisible(buttonMeal, Button.GONE);
 	   			checkVisible(buttonStartTemporaryBasal, Button.INVISIBLE);
 	   			checkVisible(buttonCancelTemporaryBasal, Button.INVISIBLE);
 	   			checkVisible(buttonHypoTreatment, Button.INVISIBLE);
 	   		
 	   			checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   			checkVisible(buttonSafety, Button.INVISIBLE);
 	   			checkVisible(buttonOpenLoop, Button.INVISIBLE);
 	   			checkVisible(buttonSensorOnly, Button.GONE);
 	   			
 	   			checkVisible(buttonPlots, Button.INVISIBLE);
 	   			checkVisible(buttonExercise, ToggleButton.INVISIBLE);
 	   			
 	   			Debug.i(TAG, FUNC_TAG, "InsulinComplete: "+insulinSetupComplete);
 	   			
 	   			if (pumpReadyNoReco() && insulinSetupComplete)
 	   			{
 	   				checkVisible(frame2, FrameLayout.VISIBLE);
 	   				checkVisible(buttonSensorOnly, Button.GONE);
 	   				if(PumpModeEnabled) checkVisible(buttonOpenLoop, Button.VISIBLE);
 	   				if (cgmReady() && insulinSetupComplete)
 	   				{
 	   					checkVisible(frame3, FrameLayout.VISIBLE);
 	   					if(ClosedLoopEnabled)
 	   						checkVisible(buttonClosedLoop, Button.VISIBLE);
 	   					else
 	   						checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   					if(SafetyModeEnabled)
 	   						checkVisible(buttonSafety, Button.VISIBLE);
 	   					else
 	   						checkVisible(buttonSafety, Button.INVISIBLE);
 	   				}
 	 				else {
 						checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   					checkVisible(buttonSafety, Button.INVISIBLE);
 	 				}
 	   			}
 	   			else if (cgmReady()) 	   			
 	   			{
 	   				checkVisible(frame3, FrameLayout.VISIBLE);
 	   				checkVisible(buttonOpenLoop, Button.GONE);
 	   				checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   				checkVisible(buttonSafety, Button.INVISIBLE);
 	   				checkVisible(buttonSensorOnly, Button.VISIBLE);
    			}
 	   			break;
 	   		case State.DIAS_STATE_CLOSED_LOOP:
 	   			checkVisible(mainButtonsHigh, FrameLayout.VISIBLE);
 	   			checkVisible(frame1, FrameLayout.VISIBLE);
 	   			checkVisible(frame2, FrameLayout.VISIBLE);
 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	   			checkVisible(frame4, FrameLayout.VISIBLE);
 	   			
 	   			checkVisible(buttonClosedLoop, Button.GONE);
 	   			if (SafetyModeEnabled)
 	   				checkVisible(buttonSafety, Button.VISIBLE);
 	   			else
 	   				checkVisible(buttonSafety, Button.INVISIBLE);
	   			if (PumpModeEnabled)
	   				checkVisible(buttonOpenLoop, Button.VISIBLE);
	   			else
	   				checkVisible(buttonOpenLoop, Button.INVISIBLE);
	   			checkVisible(buttonSensorOnly, Button.GONE);
 	   			checkVisible(buttonStop, Button.VISIBLE);
 	   			
	   			if (EXERCISING)
	   			{
		   			buttonExercise.setBackgroundResource(R.drawable.button_exercising);
	   				buttonExercise.setText("");
		   		} 
	   			else 
		   		{
	   				buttonExercise.setBackgroundResource(R.drawable.button_not_exercising);
		   			buttonExercise.setText("");
		   		}
	   			checkVisible(buttonExercise, ToggleButton.VISIBLE);
	   			
 	   			if (tbrAvailable >= TempBasal.MODE_AVAILABLE_CL) {
 	   				checkVisible(frame3, FrameLayout.VISIBLE);
 	   				if (temporaryBasalRateActive()) {
 	 	 	   			checkVisible(buttonStartTemporaryBasal, Button.GONE);
 	 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.VISIBLE);
 	   				}
 	   				else {
 	 	 	   			checkVisible(buttonStartTemporaryBasal, Button.VISIBLE);
 	 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.GONE);
 	   				}
 	   			}
 	   			else {
 	 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	 	   			checkVisible(buttonStartTemporaryBasal, Button.INVISIBLE);
 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.INVISIBLE);
 	   			}
	   			
	   			checkVisible(buttonHypoTreatment, Button.VISIBLE);
	   			checkVisible(buttonMeal, Button.VISIBLE);
	   			checkVisible(infoScreen, LinearLayout.VISIBLE);
	   			checkVisible(infoCGMStatus, LinearLayout.VISIBLE);
	   			checkVisible(infoExtra, LinearLayout.VISIBLE);
	   			checkVisible(buttonPlots, Button.VISIBLE);
	   			
				break;
 	   		case State.DIAS_STATE_SAFETY_ONLY:
 	   			checkVisible(frame1, FrameLayout.VISIBLE);
 	   			checkVisible(frame2, FrameLayout.VISIBLE);
 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	   			checkVisible(frame4, FrameLayout.VISIBLE);
 	   			checkVisible(infoScreen, LinearLayout.VISIBLE);
 	   			checkVisible(infoCGMStatus, LinearLayout.VISIBLE);
 	   			checkVisible(infoExtra, LinearLayout.VISIBLE);
 	   			
 	   			if (ClosedLoopEnabled)
 	   				checkVisible(buttonClosedLoop, Button.VISIBLE);
 	   			else
 	   				checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   			checkVisible(buttonSafety, Button.GONE);
 	   			if(PumpModeEnabled)
 	   				checkVisible(buttonOpenLoop, Button.VISIBLE);
 	   			else
 	   				checkVisible(buttonOpenLoop, Button.INVISIBLE);
 	   			checkVisible(buttonSensorOnly, Button.GONE);
 	   			checkVisible(buttonStop, Button.VISIBLE);
 	   			
 	   			if (EXERCISING){
 	   				buttonExercise.setBackgroundResource(R.drawable.button_exercising);
 	   				buttonExercise.setText("");
 	   			} else {
 	   				buttonExercise.setBackgroundResource(R.drawable.button_not_exercising);
 	   				buttonExercise.setText("");
 	   			}
 	   			
 	   			checkVisible(buttonExercise, ToggleButton.VISIBLE);
 	   			
	   			// Temporary Basal Rate not available in Safety
 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	   			checkVisible(buttonStartTemporaryBasal, Button.INVISIBLE);
 	   			checkVisible(buttonCancelTemporaryBasal, Button.INVISIBLE);
   			
 	   			
 	   			checkVisible(buttonHypoTreatment, Button.VISIBLE);
 	   			checkVisible(buttonMeal, Button.VISIBLE);
 	   			checkVisible(buttonPlots, Button.VISIBLE);
 	   			buttonStop.setClickable(true);
 	   			break;
 	   		case State.DIAS_STATE_OPEN_LOOP:
 	   			checkVisible(mainButtonsHigh, FrameLayout.VISIBLE);
 	   			checkVisible(frame1, FrameLayout.VISIBLE);
 	   			checkVisible(frame2, FrameLayout.VISIBLE);
 	   			if (tbrAvailable == TempBasal.MODE_AVAILABLE_PUMP || tbrAvailable == TempBasal.MODE_NAVAILABLE_PUMP_AND_CL) {
 	   				checkVisible(frame3, FrameLayout.VISIBLE);
 	   				if (temporaryBasalRateActive()) {
 	 	 	   			checkVisible(buttonStartTemporaryBasal, Button.GONE);
 	 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.VISIBLE);
 	   				}
 	   				else {
 	 	 	   			checkVisible(buttonStartTemporaryBasal, Button.VISIBLE);
 	 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.GONE);
 	   				}
 	   			}
 	   			else {
 	 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	 	   			checkVisible(buttonStartTemporaryBasal, Button.INVISIBLE);
 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.INVISIBLE);
 	   			}
 	   			checkVisible(frame4, FrameLayout.VISIBLE);
 	   			checkVisible(buttonSensorOnly, Button.GONE);
 	   			checkVisible(buttonOpenLoop, Button.GONE);
 	   			checkVisible(buttonSafety, Button.VISIBLE);
 	   			
 	   			if (EXERCISING)
 	   			{
 		   			buttonExercise.setBackgroundResource(R.drawable.button_exercising);
 	   				buttonExercise.setText("");
 		   		} 
 	   			else 
 		   		{
 	   				buttonExercise.setBackgroundResource(R.drawable.button_not_exercising);
 		   			buttonExercise.setText("");
 		   		}
 	   			
 	   			checkVisible(buttonExercise, ToggleButton.VISIBLE);
 	   			
 	   			checkVisible(buttonHypoTreatment, Button.VISIBLE);
 	   			checkVisible(buttonMeal, Button.VISIBLE);
 	   			checkVisible(infoScreen, LinearLayout.VISIBLE);
 	   			checkVisible(infoCGMStatus, LinearLayout.VISIBLE);
 	   			checkVisible(infoExtra, LinearLayout.VISIBLE);
 	   			checkVisible(buttonPlots, Button.VISIBLE);
 	   			
 	   			if (cgmReady()) 
 				{
 					if(ClosedLoopEnabled)
 						checkVisible(buttonClosedLoop, Button.VISIBLE);
 					else
 						checkVisible(buttonClosedLoop, Button.INVISIBLE);
	   				if(SafetyModeEnabled)
	   					checkVisible(buttonSafety, Button.VISIBLE);
	   				else
	   					checkVisible(buttonSafety, Button.INVISIBLE);
 				}
 				else {
					checkVisible(buttonClosedLoop, Button.INVISIBLE);
   					checkVisible(buttonSafety, Button.INVISIBLE);
 				}
 				checkVisible(buttonStop, Button.VISIBLE);	  	
 				break;
 	   		case State.DIAS_STATE_SENSOR_ONLY:
 	   			checkVisible(mainButtonsHigh, FrameLayout.VISIBLE);
 	   			checkVisible(frame1, FrameLayout.VISIBLE);
 	   			checkVisible(frame2, FrameLayout.VISIBLE);
 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	   			checkVisible(frame4, FrameLayout.VISIBLE);
 	   			checkVisible(buttonSafety, Button.GONE);
 	   			checkVisible(buttonOpenLoop, Button.INVISIBLE);
 	   			checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   			checkVisible(buttonSensorOnly, Button.GONE);
 	   			checkVisible(buttonExercise, ToggleButton.INVISIBLE);
 	   			checkVisible(buttonStartTemporaryBasal, Button.INVISIBLE);
 	   			checkVisible(buttonCancelTemporaryBasal, Button.INVISIBLE);
 	   			if (pumpReadyNoReco() && insulinSetupComplete)
	   			{
 	   				Debug.i(TAG, FUNC_TAG, "=== Pump in Sensor Only");
	   				if(PumpModeEnabled)
	   				{
	   					checkVisible(buttonOpenLoop, Button.VISIBLE);
	   				}
	   				if (cgmReady())
	   				{
	   					Debug.i(TAG, FUNC_TAG, "=== Pump+CGM in Sensor Only");
	   					if(ClosedLoopEnabled) checkVisible(buttonClosedLoop, Button.VISIBLE);
	   					if(SafetyModeEnabled) checkVisible(buttonSafety, Button.VISIBLE);
	   				}
	   			}
	   			else if (cgmReady())
	   			{
	   				Debug.i(TAG, FUNC_TAG, "=== CGM in Sensor Only");
	   				if(true) 
	   				{
	   					Debug.i(TAG, FUNC_TAG, "=== CGM+Battery in Sensor Only");
		   				checkVisible(buttonOpenLoop, Button.INVISIBLE);
		   				checkVisible(buttonClosedLoop, Button.INVISIBLE);
	   				}
	   			}
 	   			checkVisible(buttonHypoTreatment, Button.VISIBLE);
 	   			checkVisible(buttonMeal, Button.GONE);
 	   			checkVisible(infoScreen, LinearLayout.VISIBLE);
 	   			checkVisible(infoCGMStatus, LinearLayout.VISIBLE);
 	   			checkVisible(infoExtra, LinearLayout.VISIBLE);
 	   			checkVisible(buttonPlots, Button.VISIBLE);
 				checkVisible(buttonStop, Button.VISIBLE);
 				break;
 	   		default:
 	   			break;
 	   }
 	   
 	   stop = System.currentTimeMillis();
 	   Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
   	    
   	/************************************************************************************************************************/
	//  Button Confirmation Functions (Pop-up Yes/No dialogs)
	/************************************************************************************************************************/
   	   	
   	private void stopConfirm() {
   		final String FUNC_TAG = "stopConfirm";
   		
   		Debug.i(TAG, "IO_TEST", "Stop Confirm Button Click!");
   		
   		Debug.i(TAG, FUNC_TAG, "STOP CONFIRM");
	    Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_STOP_CLICK);
		startService(intent1); 		
   	}
   	   	
   	private void openLoopConfirm(){
   		final String FUNC_TAG = "openLoopConfirm";
   		
   		Debug.i(TAG, "IO_TEST", "Pump Mode Confirm Button Click!");
   		Debug.i(TAG, FUNC_TAG, "OPEN LOOP CONFIRM");
   		
   		final Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_OPEN_LOOP_CLICK);
		startService(intent1);		
   	}
   	   	
   	private void safetyConfirm(){
   		final String FUNC_TAG = "openLoopConfirm";
   		
   		Debug.i(TAG, "IO_TEST", "Safety Confirm Button Click!");
   		Debug.i(TAG, FUNC_TAG, "SAFETY CONFIRM");
   		
   		final Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SAFETY_CLICK);
		startService(intent1);		
   	}
   	   	
   	private void closedLoopConfirm(){
		Debug.i(TAG, "IO_TEST", "Closed Loop Confirm Button Click!");
		
		final Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_CLOSED_LOOP_CLICK);  
		startService(intent1);		
   	}
   	   	
   	private void sensorOnlyConfirm(){
   		Debug.i(TAG, "IO_TEST", "Sensory Only Confirm Button Click!");
   		
		Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK);
		startService(intent1);		 		
   	}
   	   	
   	private void goToSetupScreen(int screen){
   		final String FUNC_TAG = "configConfirm";
   		
		Debug.i(TAG, FUNC_TAG, "screen="+screen);		
		
		Intent i = new Intent();
		i.setComponent(new ComponentName("edu.virginia.dtc.DiAsSetup", "edu.virginia.dtc.DiAsSetup.DiAsSetup"));
		i.putExtra("setupScreenIDNumber", screen);
		startActivity(i);
		
		int pid = android.os.Process.myPid();
		android.os.Process.killProcess(pid);		
	}
   	   	
   	private void exerciseConfirm(){ 
   		final String FUNC_TAG = "exerciseConfirm";
   		
        Button exercise = (Button) findViewById(R.id.buttonExercise);
        
        if (EXERCISING)
        {
        	exercise.setBackgroundResource(R.drawable.button_not_exercising);
     	   	exercise.setText("");
     	   	
     	   	Debug.i(TAG, FUNC_TAG, "Not exercising");		   
     	   	Intent intent1 = new Intent();
     	   	intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
     	   	intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_SET_EXERCISE_STATE);
     	   	intent1.putExtra("currentlyExercising", false);
     	   	startService(intent1);
     	   	
     	    Intent exerciseStop = new Intent();
			String action = "edu.virginia.dtc.DiAsUI.exerciseStopAction";
			exerciseStop.setAction(action);
			sendBroadcast(exerciseStop);
        } 
        else 
        {
        	exercise.setBackgroundResource(R.drawable.button_exercising);
     	   	exercise.setText("");
     	   	
     	   	Debug.i(TAG, FUNC_TAG, "Exercising");
	       	Intent intent1 = new Intent();
	       	intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
	   		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_SET_EXERCISE_STATE);
	   		intent1.putExtra("currentlyExercising", true);
	   		startService(intent1);
	   		
	   		Intent exerciseStart = new Intent();
			String action = "edu.virginia.dtc.DiAsUI.exerciseStartAction";
			exerciseStart.setAction(action);
			sendBroadcast(exerciseStart);
        }
     }
   	   	
   	private void hypoTreatmentConfirm(boolean didTreatHypo)
    {
   		final String FUNC_TAG = "hypoTreatmentConfirm";
   		
   		if(didTreatHypo)
   		{
   			Debug.i(TAG, FUNC_TAG, "Adding Hypo event...");
	   		Bundle b = new Bundle();
			b.putString("description", "Hypo treatment button pressed");
			Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_HYPO_TREATMENT, Event.makeJsonString(b), Event.SET_LOG);
   		}   	
    }
   	     
    private void homeConfirm(){
 		Intent homeIntent =  new Intent(Intent.ACTION_MAIN, null);
 		homeIntent.addCategory(Intent.CATEGORY_HOME);
 		homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
 		startActivity(homeIntent);
 		sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));    	
     }
	
    /************************************************************************************************************************/
	//  Utility Functions (Sounds, DB checks, time, etc.)
	/************************************************************************************************************************/
    
    private void newSubject()
   	{
    	final String FUNC_TAG = "newSubject";
    	//This is called when a new subject is started (Context Menu button)
    	
		// Delete everything in the biometricsContentProvider
		main.getContentResolver().delete(Uri.parse("content://"+ Biometrics.PROVIDER_NAME + "/all"), null, null);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Reboot required")
				.setMessage("The database has been cleared. Please reboot the phone.")
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						// Do nothing
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
		
   	}

	private long getTimeSeconds() 
	{
		if (SIM_TIME > 0) 
			return SIM_TIME;			//Simulated time passed on timer tick
		else 
			return (long)(System.currentTimeMillis()/1000);
	}
    
    private boolean standaloneDriverAvailable() {
   		//Does a quick scan to check if the StandaloneDriver application is installed, if so it returns true
   		final PackageManager pm = this.getPackageManager();
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

		for(ApplicationInfo a: packages)
		{
			if(a.packageName.equalsIgnoreCase("edu.virginia.dtc.standaloneDriver"))
			{
				return true;
			}
		}
   		return false;
   	}
    
    
	private void cancelTemporaryBasalRate() {
		final String FUNC_TAG = "cancelTemporaryBasalRate";
	 	Button buttonStartTemporaryBasal = (Button)this.findViewById(R.id.buttonStartTemporaryBasal);
	 	Button buttonCancelTemporaryBasal = (Button)this.findViewById(R.id.buttonCancelTemporaryBasal);
  		checkVisible(buttonStartTemporaryBasal, Button.VISIBLE);
  		checkVisible(buttonCancelTemporaryBasal, Button.GONE);

  		ContentValues values = new ContentValues();
	    long time = getTimeSeconds();
	    values.put("actual_end_time", time);
	    values.put("status_code", TempBasal.TEMP_BASAL_MANUAL_CANCEL);
	    
	    int id_to_update = -1;
	    Cursor c = getContentResolver().query(Biometrics.TEMP_BASAL_URI, new String[] {"_id"}, null, null, "start_time DESC LIMIT 1");
	    if (c.moveToLast()) {
	    	id_to_update = c.getInt(c.getColumnIndex("_id"));
	    }
	    
		Bundle b = new Bundle();
		try 
	    {
	    	getContentResolver().update(Biometrics.TEMP_BASAL_URI, values, "_id = "+id_to_update, null);
 	    	b.putString("description", "DiAsMain > cancelTempBasalDelivery, time= "+time);
 	    	Event.addEvent(getApplicationContext(), Event.EVENT_TEMP_BASAL_CANCELED, Event.makeJsonString(b), Event.SET_LOG);
	    }
	    catch (Exception e) 
	    {
	    	Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
 	    	b.putString("description", "DiAsMain > cancelTempBasalDelivery failed, time= "+time+", "+e.getMessage());
 	    	Event.addEvent(getApplicationContext(), Event.EVENT_TEMP_BASAL_CANCELED, Event.makeJsonString(b), Event.SET_LOG);
	    }
		Toast.makeText(getApplicationContext(), FUNC_TAG+", actual_end_time="+values.getAsLong("actual_end_time"), Toast.LENGTH_SHORT).show();
	}
	
	private boolean temporaryBasalRateActive() {
		final String FUNC_TAG = "temporaryBasalRateActive";
		boolean retValue = false;
		Cursor c = getContentResolver().query(Biometrics.TEMP_BASAL_URI, null, null, null, "start_time DESC LIMIT 1");
       	if(c.moveToLast()) {
   			long time = getTimeSeconds();
   			long start_time = c.getLong(c.getColumnIndex("start_time"));
   			long scheduled_end_time = c.getLong(c.getColumnIndex("scheduled_end_time"));
   			int status_code = c.getInt(c.getColumnIndex("status_code"));
   			if(time >= start_time && time <= scheduled_end_time && status_code == TempBasal.TEMP_BASAL_RUNNING) {     				
   				retValue = true;
       			Debug.i(TAG, FUNC_TAG, "Temporary Basal Rate is active.");
       		}
   			else if (time > scheduled_end_time && status_code == TempBasal.TEMP_BASAL_RUNNING) {
   				ContentValues values = new ContentValues();
   				values.put("status_code", TempBasal.TEMP_BASAL_DURATION_EXPIRED);
   				values.put("actual_end_time", time);
   				int id = c.getInt(c.getColumnIndex("_id"));
   				getContentResolver().update(Biometrics.TEMP_BASAL_URI, values, "_id = "+id, null);
   				Debug.i(TAG, FUNC_TAG, "Temporary Basal Rate expired, updating status.");
   			}
       	}
       	c.close();
		return retValue;
	}
	
    private boolean isMyServiceRunning() {
    	final String FUNC_TAG = "isMyServiceRunning";
    	
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
        	Debug.i(TAG, FUNC_TAG, service.service.getClassName());
            if ("edu.virginia.dtc.DiAsService.DiAsService".equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    /************************************************************************************************************************/
	//  Log Functions
	/************************************************************************************************************************/
    
 	private void log_action(String service, String action) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("time", (long)(System.currentTimeMillis()/1000));
        sendBroadcast(i);
	}
}
