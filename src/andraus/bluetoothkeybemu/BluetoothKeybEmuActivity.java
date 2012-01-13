package andraus.bluetoothkeybemu;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import andraus.bluetoothkeybemu.helper.BluetoothConnHelper;
import andraus.bluetoothkeybemu.helper.BluetoothConnHelperFactory;
import andraus.bluetoothkeybemu.helper.CleanupExceptionHandler;
import andraus.bluetoothkeybemu.sock.HidProtocolHelper;
import andraus.bluetoothkeybemu.sock.SocketManager;
import andraus.bluetoothkeybemu.util.DoLog;
import andraus.bluetoothkeybemu.view.BluetoothDeviceView;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class BluetoothKeybEmuActivity extends Activity {
	
	public static String TAG = "BluetoothKeyb";
	
    private static final int HANDLER_MONITOR_SOCKET = 0;
    private static final int HANDLER_MONITOR_PAIRING = 1;
    private static final int HANDLER_CONNECT = 2;

	private static String PREF_FILE = "pref";
	private static String PREF_KEY_DEVICE = "selected_device";
	
	private static int BLUETOOTH_REQUEST_OK = 1;
	private static int BLUETOOTH_DISCOVERABLE_DURATION = 300;
	
	private enum StatusIconStates { OFF, ON, INTERMEDIATE };
	
	private StatusIconStates mStatusState = StatusIconStates.OFF;
	
	private TextView mStatusTextView = null;
	private Spinner mDeviceSpinner = null;
	
	private TextView mCtrlTextView = null;
	
	private ImageView mTouchpadImageView = null;
	
	private BluetoothDeviceArrayAdapter mBluetoothDeviceArrayAdapter = null;
	
	private BluetoothAdapter mBluetoothAdapter = null;
	
	private SocketManager mSocketManager = null;
	private BluetoothConnHelper mConnHelper = null;

	/**
	 * Register intent filters for this activity
	 */
	private void registerIntentFilters() {
        IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, intentFilter);
	}
	
	/**
	 * 
	 */
	private void populateBluetoothDeviceCombo() {
	    SharedPreferences pref = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        String storedDeviceAddr = pref.getString(PREF_KEY_DEVICE, null);
        DoLog.d(TAG, "restored from pref :" + storedDeviceAddr);
        
        Set<BluetoothDevice> deviceSet = mBluetoothAdapter.getBondedDevices();
        Set<BluetoothDeviceView> deviceViewSet = new HashSet<BluetoothDeviceView>();
        for (BluetoothDevice device: deviceSet) {
            BluetoothDeviceView deviceView = new BluetoothDeviceView(device);
            deviceViewSet.add(deviceView);
        }
        
        mBluetoothDeviceArrayAdapter = new BluetoothDeviceArrayAdapter(this, deviceViewSet);
        mBluetoothDeviceArrayAdapter.sort(BluetoothDeviceView.getComparator());
        
        int posStoredDevice = mBluetoothDeviceArrayAdapter.getPositionByAddress(storedDeviceAddr);
        
        mDeviceSpinner.setAdapter(mBluetoothDeviceArrayAdapter);
        if (posStoredDevice >= 0) {
            mDeviceSpinner.setSelection(posStoredDevice);
        }
        mDeviceSpinner.setOnItemSelectedListener(mSelectDeviceListener);
	}

	/**
	 * Customize bluetooth adapter
	 */
	private void setupBluetoothAdapter() {
        int originalClass = mConnHelper.getBluetoothDeviceClass(mBluetoothAdapter);
        DoLog.d(TAG, "original class = 0x" + Integer.toHexString(originalClass));

        int err = mConnHelper.spoofBluetoothDeviceClass(mBluetoothAdapter, 0x002540);
        DoLog.d(TAG, "set class ret = " + err);

        int sdpRecHandle = mConnHelper.addHidDeviceSdpRecord(mBluetoothAdapter);
        
        DoLog.d(TAG, "SDP record handle = " + Integer.toHexString(sdpRecHandle));
	}
	
	/**
	 * Initialize UI elements
	 */
	private void setupApp() {
		setContentView(R.layout.main);
		mCtrlTextView = (TextView) findViewById(R.id.CtrlTextView);
		
		mDeviceSpinner = (Spinner) findViewById(R.id.DeviceSpinner);
		mStatusTextView = (TextView) findViewById(R.id.StatusTextView);
		
		mTouchpadImageView = (ImageView) findViewById(R.id.TouchpadImageView);
		mTouchpadImageView.setVisibility(ImageView.GONE);
		
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mConnHelper = BluetoothConnHelperFactory.getInstance(getApplicationContext());
        
        mSocketManager = new SocketManager(mConnHelper);
        
        
        registerIntentFilters();
        
        if (mBluetoothAdapter.getBondedDevices().isEmpty()) {
            showNoBondedDevicesDialog();
        }
        
        populateBluetoothDeviceCombo();
        
	}
	
	/**
	 * Updates UI
	 * @param state
	 */
	private void setStatusIconState(StatusIconStates state) {

	    if (state == mStatusState) {
	        return;
	    }
	    
        Animation animation = null;
	    switch (state) {
	    case ON:
	        if ((animation = mStatusTextView.getAnimation()) != null) {
	            animation.cancel();
	            mStatusTextView.setAnimation(null);
	        }
	        mStatusTextView.setTextColor(Color.GREEN);
	        mStatusTextView.setShadowLayer(6, 0f, 0f, Color.GREEN);
	        mStatusTextView.setText(getResources().getString(R.string.msg_status_connected));
	        
	        mTouchpadImageView.setVisibility(ImageView.VISIBLE);
	        
	        break;
	    case OFF:
            if ((animation = mStatusTextView.getAnimation()) != null) {
                animation.cancel();
                mStatusTextView.setAnimation(null);
            }
            mStatusTextView.setTextColor(Color.RED);
            mStatusTextView.setShadowLayer(6, 0f, 0f, Color.RED);
            mStatusTextView.setText(getResources().getString(R.string.msg_status_disconnected));
            
            mTouchpadImageView.setVisibility(ImageView.GONE);
	        break;
	    case INTERMEDIATE:
	        
	        mStatusTextView.setTextColor(0xffffff00);
	        mStatusTextView.setShadowLayer(6, 0f, 0f, 0xffffff00);
            mStatusTextView.setText(getResources().getString(R.string.msg_status_connecting));
	        
            AlphaAnimation alphaAnim = new AlphaAnimation(1, 0.2f);
            alphaAnim.setDuration(250);
            alphaAnim.setInterpolator(new DecelerateInterpolator(10f));
            alphaAnim.setRepeatCount(Animation.INFINITE);
            alphaAnim.setRepeatMode(Animation.REVERSE);
            
            mStatusTextView.startAnimation(alphaAnim);
            
            mTouchpadImageView.setVisibility(ImageView.GONE);
            
	        break;
	    }
	    mStatusState = state;

	}

	/**
	 * Adapter view for paired devices
	 */
	AdapterView.OnItemSelectedListener mSelectDeviceListener = 
			new AdapterView.OnItemSelectedListener() {

				@Override
				public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
					SharedPreferences pref = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
					SharedPreferences.Editor editor = pref.edit();
					
					BluetoothDeviceView device = (BluetoothDeviceView) mDeviceSpinner.getSelectedItem();
					
					editor.putString(PREF_KEY_DEVICE, device.getAddress());
					editor.apply();
					
					mThreadMonitorHandler.removeMessages(HANDLER_MONITOR_SOCKET);
					mThreadMonitorHandler.removeMessages(HANDLER_CONNECT);
					
					stopSockets(true);
				}

				@Override
				public void onNothingSelected(AdapterView<?> adapterView) {
					// TODO Auto-generated method stub
					
				}
		
	};
	
    /**
     * 
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setupApp();
        Thread.setDefaultUncaughtExceptionHandler(new CleanupExceptionHandler(mConnHelper));

        if (!mConnHelper.validateBluetoothAdapter(mBluetoothAdapter) || !mConnHelper.setup()) {
            Toast.makeText(getApplicationContext(), mConnHelper.getSetupErrorMsg(), Toast.LENGTH_LONG).show();
            finish();
        } else {
            setupBluetoothAdapter();
        }
    }
    
    /**
     * 
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    /**
     * 
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_quit:
            finish();
            break;
        case R.id.menu_refresh_devices:
            populateBluetoothDeviceCombo();
            break;
            
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 
     */
    @Override
    protected void onDestroy() {
        DoLog.d(TAG, "...being destroyed");
        unregisterReceiver(mBluetoothReceiver);
        stopSockets(false);
        if (mConnHelper != null) {
            mConnHelper.cleanup();
        }
        
        mSocketManager.destroyThreads();
        mSocketManager = null;
        
        super.onDestroy();
    }
    
    /**
     * key down
     */
    @Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
        
        mSocketManager.sendKeyCode(keyCode);
		
        return super.onKeyDown(keyCode, event);
	}

    /**
     * key up
     */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
	    
	    mSocketManager.sendKeyCode(HidProtocolHelper.NULL);

	    return super.onKeyUp(keyCode, event);
	}
	
	/**
	 *
	 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if (requestCode == BLUETOOTH_REQUEST_OK && resultCode == BLUETOOTH_DISCOVERABLE_DURATION) {
	        
	        mThreadMonitorHandler.sendEmptyMessage(HANDLER_MONITOR_PAIRING);

	    } else if (requestCode == BLUETOOTH_REQUEST_OK && resultCode == RESULT_CANCELED) {
	        finish();
	    }
	    
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 
     */
    private void showNoBondedDevicesDialog() {
	    DialogInterface.OnClickListener bondedDialogClickListener = new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                
                case DialogInterface.BUTTON_NEUTRAL:
                    Intent bluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    bluetoothIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, BLUETOOTH_DISCOVERABLE_DURATION);
                    startActivityForResult(bluetoothIntent, BLUETOOTH_REQUEST_OK);
                    break;
                }
                
            }
        };
        
	    AlertDialog dialog =  new AlertDialog.Builder(this).create();
	    dialog.setTitle(R.string.msg_dialog_no_bonded_devices_title);
	    dialog.setMessage(getResources().getString(R.string.msg_dialog_no_bonded_devices_text));
	    dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getResources().getString(android.R.string.ok), bondedDialogClickListener);
	    
	    dialog.show();
	}


    /**
     * Stop L2CAP HID connections
     */
    private void stopSockets(boolean reconnect) {

		mSocketManager.stopSockets();
		
		if (reconnect) {
		    mThreadMonitorHandler.sendEmptyMessageDelayed(HANDLER_CONNECT, 1500 /*ms */);
		} 
    }
    
    /**
     * Check socket and connection states and update UI accordingly
     */
    private void monitorSocketStates() {
        
        if (mSocketManager == null) {
            return;
        }
        SocketManager sm = mSocketManager;

        if (sm.checkState(SocketManager.STATE_NONE) || sm.checkState(SocketManager.STATE_DROPPING)) {
    		mCtrlTextView.setText("a thread stopped");
    		
            mTouchpadImageView.setOnClickListener(null);
            mTouchpadImageView.setOnTouchListener(null);

    	} else if (sm.checkState(SocketManager.STATE_WAITING)) {
            mCtrlTextView.setText("a thread waiting");
    		mThreadMonitorHandler.sendEmptyMessageDelayed(HANDLER_MONITOR_SOCKET, 1000 /*ms */);

    	} else if (sm.checkState(SocketManager.STATE_DROPPED)) {
            mCtrlTextView.setText("a thread dropped. retrying...");
            
            mTouchpadImageView.setOnClickListener(null);
            mTouchpadImageView.setOnTouchListener(null);

            setStatusIconState(StatusIconStates.INTERMEDIATE);
            
            mThreadMonitorHandler.sendEmptyMessageDelayed(HANDLER_CONNECT, 5000 /*ms */);
    	
    	} else if (sm.checkState(SocketManager.STATE_ACCEPTED)) {
            mCtrlTextView.setText("a thread accepted");
    		setStatusIconState(StatusIconStates.ON);
    		
    		TouchpadListener mTouchpadListener = new TouchpadListener(getApplicationContext(), mSocketManager);
    	    mTouchpadImageView.setOnClickListener(mTouchpadListener);
    	    mTouchpadImageView.setOnTouchListener(mTouchpadListener);
    		
    		mThreadMonitorHandler.sendEmptyMessageDelayed(HANDLER_MONITOR_SOCKET, 200 /*ms */);
    	}
    }
    
    /**
     * Main handler to deal with UI events
     */
    private Handler mThreadMonitorHandler = new  Handler() {

    	@Override
    	public void handleMessage(Message msg) {
    	    
    	    //DoLog.d(TAG, String.format("handleMessage(%d)", msg.what));
    	    
    	    switch (msg.what) {
    	    case HANDLER_MONITOR_SOCKET:
    	        monitorSocketStates();
    			break;
    			
    	    case HANDLER_MONITOR_PAIRING:
    	        DoLog.d(TAG, "waiting for a device to show up...");
    	        if (mBluetoothAdapter.getBondedDevices().isEmpty()) {
    	            sendEmptyMessageDelayed(HANDLER_MONITOR_PAIRING, 500 /* ms */);
    	        } else {
    	            populateBluetoothDeviceCombo();
    	        }
    	        
    	        break;
    	        
    	    case HANDLER_CONNECT:
    	        setStatusIconState(StatusIconStates.INTERMEDIATE);

    	        mSocketManager.startSockets(mBluetoothAdapter, ((BluetoothDeviceView) mDeviceSpinner.getSelectedItem()).getBluetoothDevice());
    	        
    	        mThreadMonitorHandler.sendEmptyMessageDelayed(HANDLER_MONITOR_SOCKET, 200);

    	        break;
    		}
    	}
    };
    
    /**
     * Receive notification of bluetooth adapter being turned off
     */
    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                DoLog.d(TAG, "BluetoothAdapter turned off. Bailing out...");
                finish();
            }
            
        }
        
    };
    
    /**
     * Custom ArrayAdapter
     *
     */
    private final class BluetoothDeviceArrayAdapter extends ArrayAdapter<BluetoothDeviceView> implements SpinnerAdapter {
    	
    	// array to store the "raw" string format
    	Map<Integer, BluetoothDeviceView> deviceMap = null;
    	
    	/**
    	 * Constructor
    	 * @param context
    	 * @param strings
    	 */
		public BluetoothDeviceArrayAdapter(Context context, Set<BluetoothDeviceView> bluetoothDeviceSet) {
			super(context, R.layout.spinner_layout);
			setDropDownViewResource(R.layout.spinner_dropdown_layout);
			
			deviceMap = new HashMap<Integer, BluetoothDeviceView>();
			int i = 0;
			for (BluetoothDeviceView deviceView:bluetoothDeviceSet ) {
				deviceMap.put(Integer.valueOf(i++), deviceView);
			}
			
		}

		@Override
		public int getCount() {
			
			return deviceMap.size();
		}

		/**
		 * Return screen-formatted value
		 */
		@Override
		public BluetoothDeviceView getItem(int i) {
			return deviceMap.get(Integer.valueOf(i));
		}

		@Override
		public long getItemId(int i) {
			return i;
		}
		
		/**
		 * Returns the array position. <b>item</b> must be raw-formatted.
		 */
		@Override
		public int getPosition(BluetoothDeviceView item) {
				
			for (int i = 0; i < deviceMap.size(); i++) {
				BluetoothDeviceView deviceView = deviceMap.get(Integer.valueOf(i));
				if (deviceView.equals(item)) {
					return i;
				}
			}
			
			return -1;
		}
		
		public int getPositionByAddress(String bluetoothAddress) {
		    for (int i = 0; i < deviceMap.size(); i++) {
		        BluetoothDeviceView deviceView = deviceMap.get(Integer.valueOf(i));
		        if (deviceView.getAddress().equals(bluetoothAddress)) {
		            return i;
		        }
		    }
		    
		    return -1;
		}
		
    }; 
    	
}