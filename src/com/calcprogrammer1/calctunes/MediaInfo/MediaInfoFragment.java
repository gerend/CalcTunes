package com.calcprogrammer1.calctunes.MediaInfo;

import java.io.File;
import java.util.ArrayList;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import com.calcprogrammer1.calctunes.Activities.CalcTunesActivity;
import com.calcprogrammer1.calctunes.AlbumArtManager;
import com.calcprogrammer1.calctunes.Dialogs.FolderSelectionDialog;
import com.calcprogrammer1.calctunes.R;
import com.calcprogrammer1.calctunes.Interfaces.MediaInfoViewInterface;
import com.calcprogrammer1.calctunes.SourceList.SourceListOperations;
import com.calcprogrammer1.calctunes.Subsonic.SubsonicAPI;
import com.calcprogrammer1.calctunes.Subsonic.SubsonicConnection;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

public class MediaInfoFragment extends Fragment
{
    private MediaInfoView                   view;
    private ImageView                       track_artwork;
    private ListView                        track_info_list;
    
    private MediaInfoAdapter                adapter;
    private ArrayList<MediaInfoListType>    adapter_data;
    
    private Bitmap                          artwork_image;

    private String                          artist;
    private String                          album;

    // Shared Preferences
    private SharedPreferences appSettings;

    OnSharedPreferenceChangeListener appSettingsListener = new OnSharedPreferenceChangeListener(){
        public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1)
        {
            appSettings = arg0;
            setTrackInfo();
        }
    };
    
    private MediaInfoViewInterface viewcallback = new MediaInfoViewInterface(){
        @Override
        public void onLayoutReloaded()
        {
            setTrackInfo();
        }
    };
    
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        //Get the application preferences
        appSettings = PreferenceManager.getDefaultSharedPreferences(getActivity());
        appSettings.registerOnSharedPreferenceChangeListener(appSettingsListener);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup group, Bundle saved)
    {
        view = new MediaInfoView(getActivity());
        view.registerCallback(viewcallback);
        Log.d("MediaInfoFragment", "OnCreateView");
        return view;
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        Log.d("MediaInfoFragment", "OnConfigurationChanged");
    }
    
    public void setTrackInfoFromFile(String track_info_path)
    {
        File file               = new File(track_info_path);
        AudioFile f             = SourceListOperations.readAudioFileReadOnly(file);
        Tag tag                 = f.getTag();
        AudioHeader header      = f.getAudioHeader();
        
        //Album Art
        artwork_image   = AlbumArtManager.getAlbumArt(tag.getFirst(FieldKey.ARTIST), tag.getFirst(FieldKey.ALBUM), getActivity(), false, true, true);
        
        //Create new adapter and adapter data
        adapter         = new MediaInfoAdapter(getActivity());
        adapter_data    = new ArrayList<MediaInfoListType>();
        
        //Title
        adapter_data.add(new MediaInfoListType( "Title",        tag.getFirst(FieldKey.TITLE)        ));
        
        //Artist
        artist = tag.getFirst(FieldKey.ARTIST);
        adapter_data.add(new MediaInfoListType( "Artist",       tag.getFirst(FieldKey.ARTIST)       ));
        
        //Album
        album = tag.getFirst(FieldKey.ALBUM);
        adapter_data.add(new MediaInfoListType( "Album",        tag.getFirst(FieldKey.ALBUM)        ));
        
        //Track Number
        adapter_data.add(new MediaInfoListType( "Track Number", tag.getFirst(FieldKey.TRACK)        ));
        
        //Track Total
        adapter_data.add(new MediaInfoListType( "Track Total",  tag.getFirst(FieldKey.TRACK_TOTAL)  ));
        
        //Disc Number
        adapter_data.add(new MediaInfoListType( "Disc Number",  tag.getFirst(FieldKey.DISC_NO)      ));
        
        //Disc Total
        adapter_data.add(new MediaInfoListType( "Total Discs",  tag.getFirst(FieldKey.DISC_TOTAL)   ));
        
        //Track Year
        adapter_data.add(new MediaInfoListType( "Year",         tag.getFirst(FieldKey.YEAR)         ));

        //Genre
        adapter_data.add(new MediaInfoListType( "Genre",        tag.getFirst(FieldKey.GENRE)        ));

        //Album Artist
        adapter_data.add(new MediaInfoListType( "Album Artist", tag.getFirst(FieldKey.ALBUM_ARTIST) ));
        
        //Composer
        adapter_data.add(new MediaInfoListType( "Composer",     tag.getFirst(FieldKey.COMPOSER)     ));
        
        //Conductor
        adapter_data.add(new MediaInfoListType( "Conductor",    tag.getFirst(FieldKey.CONDUCTOR)    ));
        
        //Duration
        adapter_data.add(new MediaInfoListType( "Track Length", "" + header.getTrackLength()        ));

        //File Path
        adapter_data.add(new MediaInfoListType( "File Path",    track_info_path                     ));

        //Format
        adapter_data.add(new MediaInfoListType( "File Format",  header.getFormat()                  ));

        //Bitrate
        adapter_data.add(new MediaInfoListType( "File Bit Rate", header.getBitRate()                ));

        //Sample Rate
        adapter_data.add(new MediaInfoListType( "Sample Rate",  "" + header.getSampleRateAsNumber() ));
        
        //Encoder
        adapter_data.add(new MediaInfoListType( "Encoder",      tag.getFirst(FieldKey.ENCODER)      ));
        
        adapter.setData(adapter_data);
        setTrackInfo();
    }

    public void setTrackInfoFromSubsonic(final SubsonicConnection sc, final int id)
    {
        //Create new adapter and adapter data
        adapter         = new MediaInfoAdapter(getActivity());
        adapter_data    = new ArrayList<MediaInfoListType>();

        Thread thread = new Thread(new Runnable()
        {
            public void run()
            {
                SubsonicAPI.SubsonicSong song = sc.subsonicapi.SubsonicGetSong(id);

                artwork_image = AlbumArtManager.getAlbumArt(song.artist, song.album, getActivity(), false, true, true);

                adapter_data.add(new MediaInfoListType("Title", song.title));
                adapter_data.add(new MediaInfoListType("Artist", song.artist));
                adapter_data.add(new MediaInfoListType("Album", song.album));
                adapter_data.add(new MediaInfoListType("Track Number", Integer.toString(song.track)));
                adapter_data.add(new MediaInfoListType("Year", Integer.toString(song.year)));
                adapter_data.add(new MediaInfoListType("Genre", song.genre));
                adapter_data.add(new MediaInfoListType("Track Length", Integer.toString(song.duration)));
                adapter_data.add(new MediaInfoListType("File Size", Integer.toString(song.size)));
                adapter_data.add(new MediaInfoListType("File Type", song.suffix));
                adapter_data.add(new MediaInfoListType("File Bit Rate", Integer.toString(song.bitRate)));
                adapter_data.add(new MediaInfoListType("Subsonic ID", Integer.toString(song.id)));
                adapter_data.add(new MediaInfoListType("Subsonic Album ID", Integer.toString(song.albumId)));
                adapter_data.add(new MediaInfoListType("Subsonic Artist ID", Integer.toString(song.artistId)));
            }
        });

        thread.start();
        try
        {
            thread.join();
        }
        catch(Exception e){}

        adapter.setData(adapter_data);
        setTrackInfo();
    }

    public void setTrackInfo()
    {
        track_artwork   = (ImageView) view.findViewById(R.id.track_artwork);
        track_info_list = (ListView)  view.findViewById(R.id.track_info_list);
        //View separator  = (View)      view.findViewById(R.id.separator);
        
        track_info_list.setDivider(null);
        track_info_list.setDividerHeight(0);
        
        track_artwork.setImageBitmap(artwork_image);
        track_info_list.setAdapter(adapter);

        registerForContextMenu(track_artwork);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////// CONTEXT MENU //////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        if(v == track_artwork)
        {
            //int position = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
            menu.add(CalcTunesActivity.CONTEXT_MENU_MEDIA_INFO, 0, Menu.NONE, "Change Album Art");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        if (item.getGroupId() == CalcTunesActivity.CONTEXT_MENU_MEDIA_INFO)
        {
            switch (item.getItemId())
            {
                case 0:
                    FolderSelectionDialog dialog = new FolderSelectionDialog(getActivity(), true);
                    dialog.setFolderSelectionDialogCallback(new FolderSelectionDialog.FolderSelectionDialogCallback()
                    {
                        @Override
                        public void onCompleted(String folderPath)
                        {
                            AlbumArtManager.replaceAlbumArtFile(artist, album, getActivity(), folderPath);
                        }
                    });
                    dialog.show();
                    break;
            }

            return true;
        }
        else
        {
            return false;
        }
    }
}
