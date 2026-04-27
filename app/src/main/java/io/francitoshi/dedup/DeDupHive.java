/*
 *  DeDupHive.java
 *
 *  Copyright (C) 2009-2026 francitoshi@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Report bugs or new features to: francitoshi@gmail.com
 */
package io.francitoshi.dedup;

import io.nut.base.collections.IterableQueue;
import io.nut.base.io.FileUtils;
import io.nut.base.util.Splitter;
import io.nut.base.util.concurrent.hive.Bee;
import io.nut.base.util.concurrent.hive.Hive;
import io.nut.headless.io.virtual.VirtualFile;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author franci
 */
public class DeDupHive implements Runnable
{
    private boolean lowmem=false;

    //añadir criterio para enlaces y archivos ocultos
    private final boolean bugs;
    private final File[] bases;
    private final BlockingQueue<File> bugQueue;
    private final BlockingQueue<VirtualFile[]> groupsQueue;
    private final File fileEof; // elemento final que marca el final de una cola de files
    private final VirtualFile[] filesEof = new VirtualFile[0];// elemento final que marca el final de una cola de arrays de files
    private final DeDupOptions options;

    private final Comparator<VirtualFile> halfCmp;
    private final Comparator<VirtualFile> fullCmp;

    final Hive hive;
    final File[] paths;
    final FileFilter[] dirsRegEx;
    final FileFilter[] filesRegEx;
    final long minWastedSize;
    final boolean wastedFilter;
    

    public DeDupHive(Hive hive, File[] bases, boolean bugs, int bufSize, DeDupOptions opt)
    {
        this.hive = hive;
        this.bases = bases;
        this.bugs = bugs;
        this.options = opt;

        this.fileEof = bases[0]; // elemento final que marca

        this.bugQueue = new LinkedBlockingQueue<>();
        this.groupsQueue = new LinkedBlockingQueue<>(bufSize);

        this.halfCmp = opt.getHalfCmp();
        this.fullCmp = opt.getFullCmp();
        
        paths = FileUtils.getAbsoluteFile(options.getFocusPaths());
        dirsRegEx = options.getFocusDirs();
        filesRegEx = options.getFocusFiles();
        minWastedSize = options.getMinWasted();
        wastedFilter = (minWastedSize != 0);
    }

    private static VirtualFile[] focusFilter(File[] paths, FileFilter[] dirsRegEx, FileFilter[] filesRegEx, VirtualFile[] list)
    {
        VirtualFile[] ret = list;
        // if any file has the correct name
        if (filesRegEx.length > 0)
        {
            for (VirtualFile item : list)
            {
                File unpacked = item.getBaseFile();
                for (FileFilter filter : filesRegEx)
                {
                    if (filter.accept(unpacked))
                    {
                        return list;
                    }
                }
            }
            ret = null;
        }

        // if any dir has the correct name
        if (dirsRegEx.length > 0)
        {
            for (VirtualFile item : list)
            {
                File unpacked = item.getBaseFile();
                for (String names : FileUtils.getParents(unpacked))
                {
                    for (FileFilter filter : dirsRegEx)
                    {
                        if (filter.accept(new File(names)))
                        {
                            return list;
                        }
                    }
                }
            }
            ret = null;
        }
        // if any file has the correct path
        if (paths.length > 0)
        {
            for (VirtualFile item : list)
            {
                File file = item.getBaseFile();
                for (File dir : paths)
                {
                    try
                    {
                        if (dir.isFile())
                        {
                            if (dir.equals(item.getBaseFile()))
                            {
                                return list;
                            }
                        }
                        else if (FileUtils.isParentOf(dir, file, false))
                        {
                                return list;
                        }
                    }
                    catch (IOException ex)
                    {
                        Logger.getLogger(DeDupHive.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            ret = null;
        }
        return ret;
    }

    private static long wastedSize(VirtualFile[] files)
    {
        if (files.length <= 1)
        {
            return 0;
        }
        long full = 0;
        long min = Long.MAX_VALUE;
        for (VirtualFile item : files)
        {
            long size = item.length();
            min = Math.min(min, item.length());
            full += size;
        }

        return (full - min);
    }

    final Bee<VirtualFile[]> minFocusBee = new Bee<>(Hive.CORES)
    {
        @Override
        protected void receive(VirtualFile[] m)
        {
            if (m.length < options.getMinCount())
            {
                return;
            }
            if (wastedFilter && wastedSize(m) < minWastedSize)
            {
                return;
            }
            if((m = focusFilter(paths, dirsRegEx, filesRegEx, m))!=null)
            {
                splitBee.send(m);
            }
        }
        @Override
        protected void terminate()
        {
            splitBee.shutdown(true);
        }
        @Override
        protected void exception(Exception ex)
        {
            System.err.println(this.getClass().getName());
            ex.printStackTrace(System.err);
        }
    };
    final Bee<VirtualFile[]> splitBee = new Bee<>(Hive.CORES)
    {
        @Override
        protected void receive(VirtualFile[] m)
        {
            if(halfCmp.equals(fullCmp))
            {
                bucketMapBee.send(m);
            }
            else
            {
                VirtualFile[][] list = Splitter.splitEquals(m,fullCmp);
                for(VirtualFile[] items : list)
                {
                    bucketMapBee.send(items);
                }
            }
        }
        @Override
        protected void terminate()
        {
            bucketMapBee.shutdown(true);
        }
        @Override
        protected void exception(Exception ex)
        {
            System.err.println(this.getClass().getName());
            ex.printStackTrace(System.err);
        }
    };
    final Bee<VirtualFile[]> bucketMapBee = new Bee<>(Hive.CORES)
    {
        @Override
        protected void receive(VirtualFile[] m)
        {
            if (m.length < options.getMinCount())
            {
                return;
            }
            if (m.length > options.getMaxCount())
            {
                return;
            }
            if (wastedFilter && wastedSize(m) < minWastedSize)
            {
                return;
            }
            if((m=focusFilter(paths, dirsRegEx, filesRegEx, m))!=null)
            {
                lowMemBee.send(m);
            }
        }
        @Override
        protected void terminate()
        {
            lowMemBee.shutdown(true);
        }
        @Override
        protected void exception(Exception ex)
        {
            System.err.println(this.getClass().getName());
            ex.printStackTrace(System.err);
        }
    };
    final Bee<VirtualFile[]> lowMemBee = new Bee<>(Hive.CORES)
    {
        @Override
        protected void receive(VirtualFile[] m)
        {
            try
            {
                if(lowmem)
                {
                    VirtualFile[] b = new VirtualFile[m.length];
                    for(int i=0;i<b.length;i++)
                    {
                        b[i] = m[i].clone();
                    }
                    m = b;
                }
                groupsQueue.put(m);
            }
            catch (CloneNotSupportedException | InterruptedException ex)
            {
                Logger.getLogger(DeDupHive.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        @Override
        protected void terminate()
        {
            try
            {
                groupsQueue.put(filesEof);
            }
            catch (InterruptedException ex)
            {
                Logger.getLogger(DeDupHive.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        @Override
        protected void exception(Exception ex)
        {
            System.err.println(this.getClass().getName());
            ex.printStackTrace(System.err);
        }
    };
    
    @Override
    public void run()
    {
        try
        {
            this.hive.add(minFocusBee, splitBee, bucketMapBee, lowMemBee);
            
            final FileHashBySize fileHashBySize = new FileHashBySize(hive, bugs, bases, options, bugQueue, fileEof, halfCmp);

            VirtualFile[][] hashes = fileHashBySize.getFileHashBySize();

            for (int i = 0; i < hashes.length; i++)
            {
                minFocusBee.send(hashes[i]);
                hashes[i] = null;
            }
            minFocusBee.shutdown(true);
            lowMemBee.awaitTermination(Integer.MAX_VALUE);
        }
        catch (IOException | InterruptedException ex)
        {
            Logger.getLogger(DeDupHive.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public Iterable<File> getBugIterable()
    {
        return new IterableQueue(groupsQueue, filesEof);
    }

    public Iterable<VirtualFile[]> getGroupsIterable()
    {
        return new IterableQueue(groupsQueue, filesEof);
    }

    public void verbose(DeDupHive from, Level level, String msg, Exception ex)
    {
        Logger.getLogger(DeDupHive.class.getName()).log(level, msg, ex);
    }

    public boolean isLowmem()
    {
        return lowmem;
    }

    public void setLowmem(boolean lowmem)
    {
        this.lowmem = lowmem;
    }
    
}
