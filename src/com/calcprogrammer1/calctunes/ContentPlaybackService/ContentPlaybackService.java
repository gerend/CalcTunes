package com.calcprogrammer1.calctunes.ContentPlaybackService;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import com.calcprogrammer1.calctunes.Activities.CalcTunesActivity;
import com.calcprogrammer1.calctunes.AlbumArtManager;
import com.calcprogrammer1.calctunes.ContentPlaylistFragment.PlaylistEditor;
import com.calcprogrammer1.calctunes.Interfaces.*;
import com.calcprogrammer1.calctunes.LastFm;
import com.calcprogrammer1.calctunes.MediaPlayer.MediaPlayerHandler;
import com.calcprogrammer1.calctunes.MediaPlayer.RemoteControlReceiver;
import com.calcprogrammer1.calctunes.R;
import com.calcprogrammer1.calctunes.Subsonic.SubsonicConnection;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.KeyEvent;


public class ContentPlaybackService extends Service
{
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////Content Type, View, and Playback Mode Constants////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public interface ContentPlaybackType
    {
        // Set now playing content to next track
        public void NextTrack();

        // Set now playing content to previous track
        public void PrevTrack();

        // Set now playing content to random track
        public void RandomTrack();

        // Set now playing content to next artist if possible
        public void NextArtist();

        // Set now playing content to previous artist if possible
        public void PrevArtist();

        // Return URI of content to play
        public String getNowPlayingUri();

        // Is playing URI a stream?
        public boolean getNowPlayingStream();

        // Get string of content
        public String getContentString();

        // Get type of content
        public int getContentType();

        // Set context
        public void setContext(Context con);

        // Called before closing the content source
        public void CleanUp();
    }

    //Content Types
    public static final int CONTENT_TYPE_NONE                   = 0;
    public static final int CONTENT_TYPE_FILESYSTEM             = 1;
    public static final int CONTENT_TYPE_LIBRARY                = 2;
    public static final int CONTENT_TYPE_PLAYLIST               = 3;
    public static final int CONTENT_TYPE_SUBSONIC               = 4;
    
    //Playback Source Types
    public static final int CONTENT_PLAYBACK_NONE               = 0;
    public static final int CONTENT_PLAYBACK_FILESYSTEM         = 1;
    public static final int CONTENT_PLAYBACK_LIBRARY            = 2;
    public static final int CONTENT_PLAYBACK_PLAYLIST           = 3;

    //Playback Order Modes
    public static final int CONTENT_PLAYBACK_MODE_IN_ORDER      = 0;
    public static final int CONTENT_PLAYBACK_MODE_RANDOM        = 1;
    public static final int CONTENT_PLAYBACK_MODE_REPEAT_ONE    = 2;
    public static final int CONTENT_PLAYBACK_MODE_REPEAT_ALBUM  = 3;
    public static final int CONTENT_PLAYBACK_MODE_REPEAT_ARTIST = 4;

    //Start-up Modes
    public static final int START_MODE_NOT_STARTED              = 0;
    public static final int START_MODE_AUTOMATIC_START          = 1;
    public static final int START_MODE_MANUAL_START             = 2;

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////Local variables////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private ContentPlaybackType content = null;
    private int     playbackMode            = CONTENT_PLAYBACK_MODE_IN_ORDER;
    private int     start_mode              = 0;
    private boolean auto_random             = false;
    private MediaPlayerHandler      mediaplayer;
    private SharedPreferences       appSettings;
    private int     multi_click_thrshld;
    private long    NextTime        = 0;
    private int     NextPressCount  = 0;
    private long    PrevTime        = 0;
    private Notification notification;
    private NotificationManager notificationManager;
    private static int notificationId = 2;
    private LastFm lastfm;
    private Timer NextTimer;

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////Callback Functions/////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    MediaPlayerHandlerInterface mediaplayerCallback = new MediaPlayerHandlerInterface(){
        public void onSongFinished()
        {
            NextTrack();
        }

        public void onStop()
        {
        }
    };
    
    OnSharedPreferenceChangeListener appSettingsListener = new OnSharedPreferenceChangeListener(){
        public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1)
        {
            appSettings = arg0;

            if(appSettings.getBoolean("service_notification", true))
            {
                initializeNotification();
            }
            else
            {
                endNotification();
            }

            multi_click_thrshld = Integer.parseInt(appSettings.getString("multi_click_thrshld", "500"));
            SetPlaybackMode(appSettings.getInt("playback_mode", CONTENT_PLAYBACK_MODE_IN_ORDER));
        }
    };
    
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////Class functions////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    //Access to MediaPlayer data
    public String NowPlayingTitle()
    {
        return mediaplayer.current_title;
    }
    
    public String NowPlayingArtist()
    {
        return mediaplayer.current_artist;
    }
    
    public String NowPlayingAlbum()
    {
        return mediaplayer.current_album;
    }
    
    public String NowPlayingYear()
    {
        return mediaplayer.current_year;
    }
    
    public int NowPlayingDuration()
    {
        return mediaplayer.getDuration();
    }
    
    public int NowPlayingPosition()
    {
        return mediaplayer.getCurrentPosition();
    }

    public void SetPlaybackMode(int mode)
    {
        playbackMode = mode;
    }

    public void SeekPlayback(int position)
    {
        mediaplayer.seekPlayback(position);
    }
    
    public boolean isPlaying()
    {
        return mediaplayer.isPlaying();
    }

    public void SetPlaybackContent(ContentPlaybackType c)
    {
        content = c;

        refreshMediaPlayer();

        updateNotification();
        notifyMediaInfoUpdated();
    }

    public void SetPlaybackContentSource(int contentType, String contentString, int contentPosition)
    {
        
        if(contentType == CONTENT_TYPE_LIBRARY)
        {
            content = new ContentPlaybackLibrary(contentString, contentPosition, getApplicationContext());
        }
        else if(contentType == CONTENT_TYPE_FILESYSTEM)
        {
            content = new ContentPlaybackFilesystem(contentString);
        }
        else if(contentType == CONTENT_TYPE_SUBSONIC)
        {
            //content = new ContentPlaybackSubsonic();
        }

        if(playbackMode == CONTENT_PLAYBACK_MODE_RANDOM || auto_random == true)
        {
            content.RandomTrack();
        }

        refreshMediaPlayer();

        updateNotification();
        notifyMediaInfoUpdated();
    }
    
    public int GetPlaybackContentType()
    {
        if(content == null)
        {
            return CONTENT_TYPE_NONE;
        }
        else
        {
            return content.getContentType();
        }
    }

    public SubsonicConnection GetPlaybackSubsonicConnection()
    {
        if(content != null && content.getContentType() == CONTENT_TYPE_SUBSONIC)
        {
            return ((ContentPlaybackSubsonic) content).getSubsonicConnection();
        }
        else
        {
            return null;
        }
    }
    public String GetPlaybackContentString()
    {
        if(content == null)
        {
            return "";
        }
        else
        {
            return content.getContentString();
        }
    }

    public String GetNowPlayingString()
    {
        if(content == null)
        {
            return "";
        }
        else
        {
            return content.getNowPlayingUri();
        }
    }

    public int GetNowPlayingSubsonicId()
    {
        if(content != null && content.getContentType() == CONTENT_TYPE_SUBSONIC)
        {
            return ((ContentPlaybackSubsonic) content).getNowPlayingId();
        }
        else
        {
            return 0;
        }
    }

    public void StartPlayback()
    {
        mediaplayer.startPlayback();
    }
    
    public void PausePlayback()
    {
        mediaplayer.pausePlayback();
    }
    
    public void StopPlayback()
    {
        if(content != null)
        content.CleanUp();

        if(mediaplayer != null)
        {
            if(mediaplayer.start_time != 0 && ((System.currentTimeMillis() / 1000L) > (mediaplayer.start_time + 30)))
            {
                lastfm.scrobble(mediaplayer.current_artist, mediaplayer.current_album, mediaplayer.current_title, mediaplayer.getDuration(), mediaplayer.start_time);
            }
            mediaplayer.stopPlayback();
        }

        updateNotification();
        notifyMediaInfoUpdated();
    }

    public void NextPressed()
    {
        if( playbackMode == CONTENT_PLAYBACK_MODE_RANDOM || auto_random == true )
        {
            RandomTrack();
        }
        else
        {
            if (System.currentTimeMillis() - NextTime < multi_click_thrshld) {
                if (NextTimer != null) {
                    NextTimer.cancel();
                }
                NextTimer = new Timer("NextTimer", true);
                NextPressCount++;
                NextTimer.schedule(new TimerTask() {
                    public void run() {
                        new ButtonPressTask().execute(3);
                    }
                }, multi_click_thrshld);
            } else {
                NextTimer = new Timer("NextTimer", true);
                NextTimer.schedule(new TimerTask() {
                    public void run() {
                        new ButtonPressTask().execute(2);
                    }
                }, multi_click_thrshld);
            }
            NextTime = System.currentTimeMillis();
        }
    }

    public void PrevPressed()
    {
        if( playbackMode == CONTENT_PLAYBACK_MODE_RANDOM || auto_random == true )
        {
            RandomTrack();
        }
        else
        {
            if (System.currentTimeMillis() - PrevTime < multi_click_thrshld)
            {
                Log.d("ContentPlaybackService", "Double clicked Prev Track");
            }
            PrevTime = System.currentTimeMillis();
            new ButtonPressTask().execute(1);
        }
    }

    public void NextTrack()
    {
        new Thread(new Runnable()
        {
            public void run()
            {
                content.NextTrack();

                refreshMediaPlayer();
                mediaplayer.startPlayback();

                updateNotification();
                notifyMediaInfoUpdated();
            }
        }).start();
    }

    public void PrevTrack()
    {
        content.PrevTrack();

        refreshMediaPlayer();
        mediaplayer.startPlayback();

        updateNotification();
        notifyMediaInfoUpdated();
    }

    public void RandomTrack()
    {
        content.RandomTrack();

        refreshMediaPlayer();
        mediaplayer.startPlayback();

        updateNotification();
        notifyMediaInfoUpdated();
    }

    public void NextArtist()
    {
        content.NextArtist();

        refreshMediaPlayer();
        mediaplayer.startPlayback();

        updateNotification();
        notifyMediaInfoUpdated();
    }

    private void refreshMediaPlayer()
    {
        if(mediaplayer != null)
        {
            if(mediaplayer.start_time != 0 && ((System.currentTimeMillis() / 1000L) > (mediaplayer.start_time + 30)))
            {
                lastfm.scrobble(mediaplayer.current_artist, mediaplayer.current_album, mediaplayer.current_title, mediaplayer.getDuration(), mediaplayer.start_time);
            }
            mediaplayer.stopPlayback();
        }
        if(content.getNowPlayingStream())
        {
            mediaplayer.initializeStream(content.getNowPlayingUri());
        }
        else
        {
            mediaplayer.initializeFile(content.getNowPlayingUri());
        }
        lastfm.updateNowPlaying(mediaplayer.current_artist, mediaplayer.current_album, mediaplayer.current_title, mediaplayer.getDuration());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////Notification Functions/////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("deprecation")
    private void initializeNotification()
    {
        updateNotification();
    }
    
    @SuppressWarnings("deprecation")
    private void updateNotification()
    {
        if(appSettings.getBoolean("service_notification", true))
        {
            Intent notificationIntent = new Intent(getApplicationContext(), CalcTunesActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent intent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

            if( !mediaplayer.current_title.equals("") )
            notification = new NotificationCompat.Builder(getApplicationContext())
                    .setContentTitle(mediaplayer.current_title)
                    .setContentText(mediaplayer.current_artist)
                    .setSmallIcon(R.drawable.icon)
                    .setLargeIcon(AlbumArtManager.getAlbumArtFromCache(mediaplayer.current_artist, mediaplayer.current_album, getApplicationContext()))
                    .setContentIntent(intent)
                    .build();
            else
            notification = new NotificationCompat.Builder(getApplicationContext())
                    .setContentTitle("CalcTunes")
                    .setContentText("Stopped")
                    .setSmallIcon(R.drawable.icon)
                    .setContentIntent(intent)
                    .build();

            startForeground(notificationId, notification);
        }
    }
    
    private void endNotification()
    {
        stopForeground(true);
    }
    
    private void notifyMediaInfoUpdated()
    {
        Intent broadcast = new Intent();
        broadcast.setAction("com.calcprogrammer1.calctunes.PLAYBACK_INFO_UPDATED_EVENT");
        sendBroadcast(broadcast);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////Service Functions//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private final IBinder mBinder = new ContentPlaybackBinder();
    
    public class ContentPlaybackBinder extends Binder
    {
        public ContentPlaybackService getService()
        {
            return ContentPlaybackService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);
        Log.d("ContentPlaybackService", "onStartCommand");
        Bundle extras = intent.getExtras();
        if(extras != null)
        {
            if((extras.getInt("auto_start", 0) == 1) && (start_mode == START_MODE_NOT_STARTED))
            {
                start_mode = START_MODE_AUTOMATIC_START;

                Log.d("ContentPlaybackService", "Automatic playback starting");

                auto_random = false;

                if(appSettings.getBoolean("auto_random", false) == true)
                {
                    auto_random = true;
                }

                SetPlaybackContentSource(CONTENT_TYPE_LIBRARY, appSettings.getString("auto_play_lib", "Music"), 0);
                StartPlayback();
            }
            else
            {
                start_mode = START_MODE_MANUAL_START;
                auto_random = false;
            }
        }
        else
        {
            start_mode  = START_MODE_MANUAL_START;
            auto_random = false;
        }
        
        return START_REDELIVER_INTENT;
    }
    
    @Override
    public void onCreate()
    {
        mediaplayer = new MediaPlayerHandler(this);
        mediaplayer.setCallback(mediaplayerCallback);
        ((AudioManager)getSystemService(AUDIO_SERVICE)).registerMediaButtonEventReceiver(new ComponentName( this, RemoteControlReceiver.class ) );
               
        //Register media buttons receiver
        registerReceiver(remoteReceiver, new IntentFilter("com.calcprogrammer1.calctunes.REMOTE_BUTTON_EVENT"));

        //Register app close receiver
        registerReceiver(closeReceiver, new IntentFilter("com.calcprogrammer1.calctunes.CLOSE_APP_EVENT"));

        //Get the application preferences
        appSettings = PreferenceManager.getDefaultSharedPreferences(this);
        appSettings.registerOnSharedPreferenceChangeListener(appSettingsListener);

        multi_click_thrshld = Integer.parseInt(appSettings.getString("multi_click_thrshld", "500"));
        SetPlaybackMode(appSettings.getInt("playback_mode", CONTENT_PLAYBACK_MODE_IN_ORDER));

        lastfm = new LastFm(this);

        if(appSettings.getBoolean("service_notification", true))
        {
            initializeNotification();
        }
    }
    
    @Override
    public void onDestroy()
    {
        //Stop the media player
        mediaplayer.stopPlayback();
        
        //Unregister media buttons receiver
        unregisterReceiver(remoteReceiver);

        //Unregister app close receiver
        unregisterReceiver(closeReceiver);

        //Stop the notification
        endNotification();
    }
    
    @Override
    public IBinder onBind(Intent arg0)
    {
        return mBinder;
    }

    public class ButtonPressTask extends AsyncTask<Object, Void, Void>
    {
        private int id;

        @Override
        protected Void doInBackground(Object... params)
        {
            id       = (Integer)params[0];
            switch(id)
            {
                case 0:
                    RandomTrack();
                    break;

                case 1:
                    PrevTrack();
                    break;

                case 2:
                    NextTrack();
                    break;

                case 3:
                    NextArtist();
                    break;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    /*---------------------------------------------------------------------*\
    |                                                                       |
    |   Remote Control Broadcast Receiver                                   |
    |                                                                       |
    |   Receives intent com.calcprogrammer1.calctunes.REMOTE_BUTTON_EVENT   |
    |                                                                       |
    |   This intent contains a KeyEvent.KEYCODE_ value indicating which     |
    |   media button key was pressed.  It is sent from the Media Buttons    |
    |   event receiver for handling headset/Bluetooth key events.           |
    |                                                                       | 
    \*---------------------------------------------------------------------*/
    
    private BroadcastReceiver remoteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.d("service", "received intent");
            int keyCode = intent.getExtras().getInt("keyEvent");
            
            switch(keyCode)
            {
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    NextPressed();
                    break;
                   
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    PrevPressed();
                    break;
                    
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    StopPlayback();
                    break;
                    
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    PausePlayback();
                    break;
                    
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    StartPlayback();
                    break;
                    
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    if(isPlaying())
                    {
                        PausePlayback();
                    }
                    else
                    {
                        StartPlayback();
                    }
                    break;
            }
        }
    };

    /*---------------------------------------------------------------------*\
    |                                                                       |
    |   Remote Control Broadcast Receiver                                   |
    |                                                                       |
    |   Receives intent com.calcprogrammer1.calctunes.REMOTE_BUTTON_EVENT   |
    |                                                                       |
    |   This intent contains a KeyEvent.KEYCODE_ value indicating which     |
    |   media button key was pressed.  It is sent from the Media Buttons    |
    |   event receiver for handling headset/Bluetooth key events.           |
    |                                                                       |
    \*---------------------------------------------------------------------*/

    private BroadcastReceiver closeReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent)
        {

            Log.d("CalcTunesActivity", "Exit Intent Received");

            if(start_mode == START_MODE_AUTOMATIC_START)
            {
                stopSelf();
            }
        }
    };
}
