/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2013, Andreas Muttscheller <asfa194@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package de.qspool.clementineremote.ui;

import java.util.LinkedList;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.ui.fragments.AbstractDrawerFragment;
import de.qspool.clementineremote.ui.fragments.PlayerFragment;
import de.qspool.clementineremote.ui.fragments.PlaylistFragment;

public class Player extends SherlockFragmentActivity {

	private SharedPreferences mSharedPref;
	private PlayerHandler mHandler;
	
	private Toast mToast;
	
	private int mCurrentFragment;
	private LinkedList<AbstractDrawerFragment> mFragments = new LinkedList<AbstractDrawerFragment>();
	
	private String[] mNavigationTitles;
    private ListView mDrawerList;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
	    setContentView(R.layout.main_activity);
	    
	    /* 
	     * Define here the available fragments in the mail layout
	     */
        mFragments.add(new PlayerFragment());
        mFragments.add(new PlaylistFragment());
	    
	    mNavigationTitles = getResources().getStringArray(R.array.navigation_array);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        // Set the adapter for the list view
        mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                R.layout.drawer_list_item, mNavigationTitles));
        // Set the list's click listener
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
		
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.connectdialog_connect,  /* "open drawer" description */
                R.string.close  /* "close drawer" description */
                ) {
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        selectItem(0);
	}
	
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }
	
	@Override
	public void onResume() {
		super.onResume();
		
		// Check if we are still connected
		if (App.mClementineConnection == null
		 || App.mClementine           == null
		 || !App.mClementineConnection.isAlive()
		 || !App.mClementine.isConnected()) {
			setResult(ConnectDialog.RESULT_DISCONNECT);
			finish();
		} else {
		    // Set the handler
		    mHandler = new PlayerHandler(this);
		    App.mClementineConnection.setUiHandler(mHandler);
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		mHandler = null;
		if (App.mClementineConnection != null) {
			App.mClementineConnection.setUiHandler(mHandler);
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			int currentVolume = App.mClementine.getVolume();
			// Control the volume of clementine if enabled in the options
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (mSharedPref.getBoolean(App.SP_KEY_USE_VOLUMEKEYS, true)) {
					Message msgDown = Message.obtain();
					msgDown.obj = ClementineMessageFactory.buildVolumeMessage(App.mClementine.getVolume() - 10);
					App.mClementineConnection.mHandler.sendMessage(msgDown);
					if (currentVolume >= 10)
						currentVolume -= 10;
					else
						currentVolume = 0;
					makeToast(getString(R.string.playler_volume) + " " + currentVolume + "%", Toast.LENGTH_SHORT);
					return true;
				}
				break;
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (mSharedPref.getBoolean(App.SP_KEY_USE_VOLUMEKEYS, true)) {
					Message msgUp = Message.obtain();
					msgUp.obj = ClementineMessageFactory.buildVolumeMessage(App.mClementine.getVolume() + 10);
					App.mClementineConnection.mHandler.sendMessage(msgUp);
					if (currentVolume > 90)
						currentVolume = 100;
					else
						currentVolume += 10;
					makeToast(getString(R.string.playler_volume) + " " + currentVolume + "%", Toast.LENGTH_SHORT);
					return true;
				}
				break;
			default: break;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
		if (mSharedPref.getBoolean(App.SP_KEY_USE_VOLUMEKEYS, true)) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				return true;
			}
		}
		return super.onKeyUp(keyCode, keyEvent);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		if (item.getItemId() == android.R.id.home) {
			if (mDrawerLayout.isDrawerOpen(mDrawerList)) {
				mDrawerLayout.closeDrawer(mDrawerList);
			} else {
				mDrawerLayout.openDrawer(mDrawerList);
			}
		}

        return super.onOptionsItemSelected(item);

	}
	
	/**
     * Request a disconnect from clementine
     */
    private void requestDisconnect() {
            // Move the request to the message
            Message msg = Message.obtain();
            msg.obj = ClementineMessage.getMessage(MsgType.DISCONNECT);
           
            // Send the request to the thread
            App.mClementineConnection.mHandler.sendMessage(msg);
    }

	
	/**
	 * Disconnect was finished, now finish this activity
	 */
	void disconnect() {
		makeToast(R.string.player_disconnected, Toast.LENGTH_SHORT);
		setResult(ConnectDialog.RESULT_DISCONNECT);
		finish();
	}
	
	/**
	 * We got a message from Clementine. Here we process it for the main activity
	 * and pass the data to the currently active fragment.
	 * Info: Errormessages were already parsed in PlayerHandler!
	 * @param clementineMessage The message from Clementine
	 */
	void MessageFromClementine(ClementineMessage clementineMessage) {
		// Update the Player Fragment
		if (mFragments.get(mCurrentFragment) != null && 
			mFragments.get(mCurrentFragment).isVisible()) {
			mFragments.get(mCurrentFragment).MessageFromClementine(clementineMessage);
		}
	}
    
    /**
     * Show text in a toast. Cancels previous toast
     * @param resId The resource id
     * @param length length
     */
    private void makeToast(int resId, int length) {
    	makeToast(getString(resId), length);
    }
    
    /**
     * Show text in a toast. Cancels previous toast
     * @param tetx The text to show
     * @param length length
     */
    private void makeToast(String text, int length) {
    	if (mToast != null) {
    		mToast.cancel();
    	}
    	mToast = Toast.makeText(this, text, length);
    	mToast.show();
    }
    
    private class DrawerItemClickListener implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView parent, View view, int position, long id) {
            selectItem(position);
        }
    }

    /** Swaps fragments in the main content view */
    private void selectItem(int position) {
    	if (position < mFragments.size()) {
    		// Create a new fragment and specify the planet to show based on position
    		FragmentManager fragmentManager = getSupportFragmentManager();
        	fragmentManager.beginTransaction().replace(R.id.content_frame, mFragments.get(position)).commit();
        	mCurrentFragment = position;
    	} else 	if (position == mFragments.size()) {
    		Intent settingsIntent = new Intent(this, ClementineSettings.class);
            startActivity(settingsIntent);
    	} else {
    		requestDisconnect();
    	}
        
        mDrawerLayout.closeDrawer(mDrawerList);
    }

}
