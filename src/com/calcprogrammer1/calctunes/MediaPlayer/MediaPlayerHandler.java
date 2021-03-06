package com.calcprogrammer1.calctunes.MediaPlayer;

import java.io.File;
import java.util.HashMap;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import com.calcprogrammer1.calctunes.Interfaces.MediaPlayerHandlerInterface;
import com.calcprogrammer1.calctunes.SourceList.SourceListOperations;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

public class MediaPlayerHandler
{
    MediaPlayer mp;
    Visualizer vis;
    LosslessMediaCodecHandler ls;
    
    boolean running = false;
    boolean prepared = false;
    boolean playonprepare = false;
    boolean audio_fx = false;
    boolean stream = false;

    public String current_path = "";
    public String current_title = "";
    public String current_album = "";
    public String current_artist = "";
    public String current_year = "";
    public long   start_time = 0;

    //Shared Preferences
    private SharedPreferences appSettings;
    
    public byte vis_buffer[];
    
    Context con;
    
    MediaPlayerHandlerInterface cb;
    
    OnSharedPreferenceChangeListener appSettingsListener = new OnSharedPreferenceChangeListener(){
        public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1)
        {
            appSettings = arg0;
            audio_fx = appSettings.getBoolean("audio_fx", true);
        }
    };
    
    public MediaPlayerHandler(Context context)
    {
        con = context;
        
        //Get the application preferences
        appSettings = PreferenceManager.getDefaultSharedPreferences(con);
        appSettings.registerOnSharedPreferenceChangeListener(appSettingsListener);
        audio_fx = appSettings.getBoolean("audio_fx", true);
    }
    
    public void setCallback(MediaPlayerHandlerInterface callback)
    {
        cb = callback;
    }

    public void initializeFile(String filePath)
    {
        current_path = filePath;
        stream = false;
        initialize();
    }

    public void initializeStream(String streamPath)
    {
        current_path = streamPath;
        stream = true;
        initialize();
    }

    @SuppressLint("NewApi")
    public void initialize()
    {
        stopPlayback();
        mp = new MediaPlayer();
        current_title = "";
        current_artist = "";
        current_album = "";
        current_year = "";
        start_time = 0;
        try
        {
            if(stream && Build.VERSION.SDK_INT >= 10)
            {
                MediaMetadataRetriever mr = new MediaMetadataRetriever();
                if (Build.VERSION.SDK_INT >= 14)
                {
                    mr.setDataSource(current_path, new HashMap<String, String>());
                }
                else
                {
                    mr.setDataSource(current_path);
                }
                current_artist = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                current_album = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                current_title = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                current_year = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
            }
            else
            {
                File song = new File(current_path);
                AudioFile f;
                f = SourceListOperations.readAudioFileReadOnly(song);
                Tag tag = f.getTag();
                current_artist = tag.getFirst(FieldKey.ARTIST);
                current_album = tag.getFirst(FieldKey.ALBUM);
                current_title = tag.getFirst(FieldKey.TITLE);
                current_year = tag.getFirst(FieldKey.YEAR);
            }

            mp.reset();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(current_path);
            mp.prepareAsync();
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
            {
                public void onPrepared(MediaPlayer arg0)
                {
                    prepared = true;
                    if(playonprepare)
                    {
                        mp.start();
                        playonprepare = false;
                    }
                } 
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
            {
                public void onCompletion(MediaPlayer arg0)
                {
                    mp.stop();
                    prepared = false;
                    Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
                    i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mp.getAudioSessionId());
                    i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, con.getPackageName());
                    con.sendBroadcast(i);
                    mp.release();
                    mp = null;
                    current_path = "";
                    if(cb != null) cb.onSongFinished();
                }
            });
            if(audio_fx)
            {
                Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
                i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mp.getAudioSessionId());
                i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, con.getPackageName());
                con.sendBroadcast(i);
            }
            if(Build.VERSION.SDK_INT > 8)
            {
                vis = new Visualizer(mp.getAudioSessionId());
            }
        }
        catch (Exception e)
        {
            mp.release();
            mp = null;
            //ls = new LosslessMediaCodecHandler();
            //ls.setCallback(new LosslessMediaCodecHandlerCallback()
            //{
//                public void onCompletion()
//                {
//                    prepared = false;
//                    ls = null;
//                    current_path = "";
//                    current_title = "";
//                    current_artist = "";
//                    current_album = "";
//                    current_year = "";
//                    if(cb != null) cb.onSongFinished();
//                }
//            });
//            ls.setDataSource(current_path);
//            prepared = true;
//            if(playonprepare)
//            {
//                ls.start();
//                playonprepare = false;
//            }
        }
    }
    
    public void startPlayback()
    {
        if(start_time == 0)
        {
            start_time = System.currentTimeMillis() / 1000L;
        }
        if(prepared)
        {
            if(mp != null)
            {
                mp.start();
            }
            else if(ls != null)
            {
                ls.start();
            }
            
            if(vis != null)
            {
                vis.setEnabled(true);
            }
        }
        else
        {
            playonprepare = true;
        }
    }
    
    public void stopPlayback()
    {
        if(prepared)
        {
            if(mp != null)
            {
                mp.stop();
                prepared = false;
                mp.release();
                mp = null;
            }
            else if(ls != null)
            {
                ls.stop();
                prepared = false;
                ls = null;
            }
            current_path = "";
            current_title = "";
            current_artist = "";
            current_album = "";
            current_year = "";
        }
        if(cb != null) cb.onStop();
    }
    
    public void pausePlayback()
    {
        if(prepared)
        {
            if(mp != null)
            {
                mp.pause();
            }
            else if(ls != null)
            {
                ls.pause();
            }
        }
    }
    
    public boolean isPlaying()
    {
        if(prepared)
        {
            if(mp != null)
            {
                return mp.isPlaying();
            }
            else if(ls != null)
            {
                return ls.isPlaying();
            }
            else
            {
                return false;
            }
        }
        else
        {
            return false;
        }
    }
    
    public void seekPlayback(int seekto)
    {
        if(prepared)
        {
            if(mp != null)
            {
                mp.pause();
                mp.seekTo(seekto);
                mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener()
                {
                    public void onSeekComplete(MediaPlayer arg0)
                    {
                        mp.start();
                    }
                });
            }
            else if(ls != null)
            {
                ls.seekTo(seekto);
            }
        }
    }
    
    public int getCurrentPosition()
    {
        if(prepared)
        {
            if(mp != null)
            {
                return mp.getCurrentPosition();
            }
            else if(ls != null)
            {
                return ls.getCurrentPosition();
            }
            else
            {
                return 0;
            }
        }
        else
        {
            return 0;
        }
    }
    
    public int getDuration()
    {
        if(prepared)
        {
            if(mp != null)
            {
                return mp.getDuration();
            }
            else if(ls != null)
            {
                return ls.getDuration();
            }
            else
            {
                return 0;
            }
        }
        else
        {
            return 0;
        }
    }
    
    public int computeFft()
    {
        if(Build.VERSION.SDK_INT > 8)
        {
            return vis.getFft(vis_buffer);
        }
        else
        {
            return 0;
        }
    }
    
    public int computeWave()
    {
        if(Build.VERSION.SDK_INT > 8)
        {
            return vis.getWaveForm(vis_buffer);
        }
        else
        {
            return 0;
        }
    }
}
