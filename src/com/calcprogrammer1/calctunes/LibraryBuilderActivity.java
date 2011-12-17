package com.calcprogrammer1.calctunes;

import java.util.ArrayList;
import com.calcprogrammer1.calctunes.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

public class LibraryBuilderActivity extends Activity
{
    ListView librarylist;
    EditText libNameInput;
    ArrayList<String> libraryFolders;
    Intent i;
    
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.librarybuilder);
        librarylist = (ListView) findViewById(R.id.libraryListView);
        libNameInput = (EditText) findViewById(R.id.libNameInput);
        
        i = getIntent();
        Bundle extras = i.getExtras();
        libraryFolders = new ArrayList<String>();
        if(extras != null)
        {
            String filename = extras.getString("EditFilename");
            String name = extras.getString("EditName");
            libraryFolders = LibraryOperations.readLibraryFile(filename);
            libNameInput.setText(name);
            updateFolderList();
        }

    }
    
    public void AddFolderClick(View view)
    {
        Intent intent = new Intent("org.openintents.action.PICK_DIRECTORY");
        startActivityForResult(intent, 1);
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(resultCode == Activity.RESULT_OK)
        {
            Uri dirname = data.getData();
            String newFolder = dirname.getPath();
            boolean match = false;
            for(int i = 0; i < libraryFolders.size(); i++)
            {
                if(newFolder.equals(libraryFolders.get(i)))
                {
                    match = true;
                }
            }
            if(!match)
            {
                libraryFolders.add(newFolder);
            }
            updateFolderList();
        }
    }
    
    public void updateFolderList()
    {
        librarylist.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, libraryFolders.toArray(new String[libraryFolders.size()])));
        librarylist.setItemsCanFocus(false);
        librarylist.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    }
    
    public void RemoveSelectedClick(View view)
    {
        ArrayList<Integer> indicesToRemove = new ArrayList<Integer>();
        for(int i = 0; i < libraryFolders.size(); i++)
        {
            if(librarylist.isItemChecked(i))
            {
                indicesToRemove.add(i);
            }
        }
        for(int i = 0; i < indicesToRemove.size(); i++)
        {
            libraryFolders.remove(indicesToRemove.get(i).intValue()-i);
        }
        updateFolderList();
    }
    
    public void DoneClick(View view)
    {
        if(libNameInput.getText().toString().equals(""))
        {
            AlertDialog ad = new AlertDialog.Builder(this).create();
            ad.setCancelable(false);
            ad.setMessage("Please input a library name to continue.");
            ad.setButton("OK", new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog, int which)
                {
                    dialog.dismiss();
                }
            });
            ad.show();
        }
        else if(libraryFolders.size() == 0)
        {
            AlertDialog ad = new AlertDialog.Builder(this).create();
            ad.setCancelable(false);
            ad.setMessage("Please select at least one folder to continue.");
            ad.setButton("OK", new DialogInterface.OnClickListener()
            {
                public void onClick(DialogInterface dialog, int which)
                {
                    dialog.dismiss();
                }
            });
            ad.show();
        }
        else
        {
            i.putStringArrayListExtra("libraryFolders", libraryFolders);
            i.putExtra("libraryName", libNameInput.getText().toString());
            setResult(Activity.RESULT_OK, i);
            finish();
        }
    }
}
