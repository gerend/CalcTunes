package com.calcprogrammer1.calctunes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class FileOperations
{
    public static File[] selectFilesOnly(File[] unsorted_files)
    {
        ArrayList<File> file_list = new ArrayList<File>();
        for(int i = 0; i < unsorted_files.length; i++)
        {
            if(!unsorted_files[i].isDirectory())
            {
                file_list.add(unsorted_files[i]);
            }
        }
        return file_list.toArray(new File[file_list.size()]);
    }
    
    public static File[] selectDirsOnly(File[] unsorted_files)
    {
        ArrayList<File> dir_list = new ArrayList<File>();
        for(int i = 0; i < unsorted_files.length; i++)
        {
            if(unsorted_files[i].isDirectory())
            {
                dir_list.add(unsorted_files[i]);
            }
        }
        return dir_list.toArray(new File[dir_list.size()]);
    }

    public static void addFiles(File file, ArrayList<File> all)
    {
        final File[] children = file.listFiles();
        if (children != null)
        {
            for (File child : children)
            {
                all.add(child);
            }
        }
    }

    public static void addFilesRecursively(File file, ArrayList<File> all)
    {
        final File[] children = file.listFiles();
        if (children != null)
        {
            for (File child : children)
            {
                all.add(child);
                addFilesRecursively(child, all);
            }
        }
    }

    public static boolean moveFile(String sourcefile, String dest)
    {
        File file = new File(sourcefile);
        File dir = new File(dest);
        return file.renameTo(new File(dir, file.getName()));
    }
    
    public static File[] sortFileListDirsFiles(File[] unsorted_files)
    {
        ArrayList<File> file_list = new ArrayList<File>();
        ArrayList<File> dir_list = new ArrayList<File>();
        for(int i = 0; i < unsorted_files.length; i++)
        {
            if(unsorted_files[i].isDirectory())
            {
                dir_list.add(unsorted_files[i]);
            }
            else
            {
                file_list.add(unsorted_files[i]);
            }
        }
        
        File[] files = new File[dir_list.size() + file_list.size()];
        int count = 0;
        for(; count < dir_list.size(); count++)
        {
            files[count] = dir_list.get(count); 
        }
        for(int j = 0; j < file_list.size(); j++)
        {
            files[j + count] = file_list.get(j);
        }
        return files;  
    }

    static public void copy(String src, String dst)
    {
        File srcFile = new File(src);
        File dstFile = new File(dst);
        copy(srcFile, dstFile);
    }

    static public void copy(File src, File dst)
    {
        try
        {
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dst);

            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0)
            {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        }
        catch(Exception e){}
    }

    static public String getExtension(String fileName)
    {
        String extension = "";

        int i = fileName.lastIndexOf('.');
        int p = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));

        if (i > p) {
            extension = fileName.substring(i+1);
        }
        return(extension);
    }
}