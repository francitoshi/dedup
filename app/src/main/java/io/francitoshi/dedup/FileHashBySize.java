/*
 * Copyright (C) 2009-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.dedup;

import io.nut.base.collections.bag.Bag;
import io.nut.base.io.FileUtils;
import io.nut.base.util.Concats;
import io.nut.base.util.concurrent.hive.Bee;
import io.nut.base.util.concurrent.hive.Hive;
import io.nut.headless.io.ForEachFileBee;
import io.nut.headless.io.virtual.VirtualFile;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author franci
 */
public class FileHashBySize
{
    private final Hive hive;
    private final boolean bugs;
    
    private final File[] bases;
    private final DeDupOptions options;
    private final BlockingQueue<File> bugQueue;
    private final File fileEof; // elemento final que marca el final de una cola de files

    final FileFilter[] dirNameRegEx;
    final FileFilter[] fileNameRegEx;
    
    final Bag<VirtualFile> sizeMap;
    
    public FileHashBySize(Hive hive, boolean bugs, File[] bases, DeDupOptions options, BlockingQueue<File> bugQueue, File fileEof, Comparator<VirtualFile> halfCmp)
    {
        this.hive = hive;
        this.bugs = bugs;
        this.bases = bases;
        this.options = options;
        this.bugQueue = bugQueue;
        this.fileEof = fileEof;
        
        this.dirNameRegEx = options.getDirNames();
        this.fileNameRegEx = options.getFileNames();
        this.sizeMap = Bag.synchronizedBag(Bag.create(halfCmp));
        
        this.hive.add(readableBee, filterDirFileBee);
    }
    
    private boolean readable(VirtualFile item)
    {
        if (item.exists())
        {
            // ignoring unreadable files
            if (item.canRead())
            {
                return true;
            }
        }
        else if (bugs)
        {
            try
            {
                if (FileUtils.isBugName(item.getBaseFile()))
                {
                    bugQueue.put(item.getBaseFile());
                }
                else
                {
                    Logger.getLogger(FileHashBySize.class.getName()).log(Level.WARNING, "wrong link \"{0}\"",item);
                }
            }
            catch (InterruptedException | IOException ex)
            {
                Logger.getLogger(FileHashBySize.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return false;
    }

    private static boolean filterDirFile(VirtualFile file, FileFilter[] dirsRegEx, FileFilter[] filesRegEx)
    {
        if (filesRegEx.length > 0)
        {
            for (FileFilter filter : filesRegEx)
            {
                if (filter.accept(file.getBaseFile()))
                {
                    return true;
                }
            }
            return false;
        }

        if (dirsRegEx.length > 0)
        {
            for (String names : FileUtils.getParents(file.getBaseFile()))
            {
                for (FileFilter filter : dirsRegEx)
                {
                    if (filter.accept(new File(names)))
                    {
                        return true;
                    }
                }
            }
            return false;
        }
        return true;
    }
    final AtomicInteger readableBeeCount = new AtomicInteger();
    final Bee<VirtualFile> readableBee = new Bee<>(Hive.CORES)
    {
        @Override
        protected void receive(VirtualFile m)
        {
            readableBeeCount.incrementAndGet();
            if(readable(m))
            {
                filterDirFileBee.accept(m);
            }
            else
            {
                System.err.printf("can't read %s\n",m);
            }
        }

        @Override
        protected void terminate()
        {
            filterDirFileBee.shutdown(true);
        }

        @Override
        protected void exception(Exception ex)
        {
            System.err.println(this.getClass().getName());
            ex.printStackTrace(System.err);
        }
        
    };

    final AtomicInteger filterDirFileBeeCount = new AtomicInteger();
    final Bee<VirtualFile> filterDirFileBee = new Bee<>(Hive.CORES)
    {
        @Override
        protected void receive(VirtualFile m)
        {
            filterDirFileBeeCount.incrementAndGet();
            if(filterDirFile(m, dirNameRegEx, fileNameRegEx))
            {
                sizeMap.add(m);
            }
        }

        @Override
        protected void terminate()
        {
            filterDirFileBee.shutdown(true);
        }
        @Override
        protected void exception(Exception ex)
        {
            System.err.println(this.getClass().getName());
            ex.printStackTrace(System.err);
        }
    };

    VirtualFile[][] getFileHashBySize() throws IOException, InterruptedException
    {
        final File[] basesAndFocus = Concats.cat(bases, options.getFocusPaths());
        //obtener ficheros en bruto
        
        hive.add(readableBee,filterDirFileBee);
        
        ForEachFileBee foreach = new ForEachFileBee(basesAndFocus, options, readableBee,  true);
        new Thread(foreach).start();

        // wait until bees are alive to obtain all items for each bucket
        readableBee.awaitTermination(Integer.MAX_VALUE);
        filterDirFileBee.awaitTermination(Integer.MAX_VALUE);
        bugQueue.put(fileEof);
                
        // now each bucket is
        VirtualFile[][] list = sizeMap.toArray(new VirtualFile[0][0]);
        return (list == null ? new VirtualFile[0][] : list);
    }
    
}
