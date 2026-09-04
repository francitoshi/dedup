/*
 * Copyright (C) 2009-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.dedup;

import io.nut.base.collections.IterableQueue;
import io.nut.base.io.FileUtils;
import io.nut.base.util.Splitter;
import io.nut.base.util.concurrent.actor.Actor;
import io.nut.base.util.concurrent.actor.ActorHub;
import io.nut.base.util.concurrent.actor.ProxyActorHub;
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
public class DeDupActor implements Runnable
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

    final ProxyActorHub hive = new ProxyActorHub();
    final File[] paths;
    final FileFilter[] dirsRegEx;
    final FileFilter[] filesRegEx;
    final long minWastedSize;
    final boolean wastedFilter;
    

    public DeDupActor(ActorHub hive, File[] bases, boolean bugs, int bufSize, DeDupOptions opt)
    {
        this.hive.setActorHub(hive);
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
                        Logger.getLogger(DeDupActor.class.getName()).log(Level.SEVERE, null, ex);
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

    final Actor<VirtualFile[]> minFocusActor = new Actor<>(hive, ActorHub.CORES, ActorHub.CORES)
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
                splitActor.accept(m);
            }
        }
        @Override
        protected void terminate()
        {
            splitActor.shutdown(true);
        }
        @Override
        protected void exception(Exception ex)
        {
            System.err.println(this.getClass().getName());
            ex.printStackTrace(System.err);
        }
    };
    final Actor<VirtualFile[]> splitActor = new Actor<>(hive, ActorHub.CORES, ActorHub.CORES)
    {
        @Override
        protected void receive(VirtualFile[] m)
        {
            if(halfCmp.equals(fullCmp))
            {
                bucketMapActor.accept(m);
            }
            else
            {
                VirtualFile[][] list = Splitter.splitEquals(m,fullCmp);
                for(VirtualFile[] items : list)
                {
                    bucketMapActor.accept(items);
                }
            }
        }
        @Override
        protected void terminate()
        {
            bucketMapActor.shutdown(true);
        }
        @Override
        protected void exception(Exception ex)
        {
            System.err.println(this.getClass().getName());
            ex.printStackTrace(System.err);
        }
    };
    final Actor<VirtualFile[]> bucketMapActor = new Actor<>(hive, ActorHub.CORES, ActorHub.CORES)
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
                lowMemActor.accept(m);
            }
        }
        @Override
        protected void terminate()
        {
            lowMemActor.shutdown(true);
        }
        @Override
        protected void exception(Exception ex)
        {
            System.err.println(this.getClass().getName());
            ex.printStackTrace(System.err);
        }
    };
    final Actor<VirtualFile[]> lowMemActor = new Actor<>(hive, ActorHub.CORES, ActorHub.CORES)
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
                Logger.getLogger(DeDupActor.class.getName()).log(Level.SEVERE, null, ex);
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
                Logger.getLogger(DeDupActor.class.getName()).log(Level.SEVERE, null, ex);
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
            final FileHashBySize fileHashBySize = new FileHashBySize(hive, bugs, bases, options, bugQueue, fileEof, halfCmp);

            VirtualFile[][] hashes = fileHashBySize.getFileHashBySize();

            for (int i = 0; i < hashes.length; i++)
            {
                minFocusActor.accept(hashes[i]);
                hashes[i] = null;
            }
            minFocusActor.shutdown(true);
            lowMemActor.awaitTermination(Integer.MAX_VALUE);
        }
        catch (IOException | InterruptedException ex)
        {
            Logger.getLogger(DeDupActor.class.getName()).log(Level.SEVERE, null, ex);
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

    public void verbose(DeDupActor from, Level level, String msg, Exception ex)
    {
        Logger.getLogger(DeDupActor.class.getName()).log(level, msg, ex);
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
