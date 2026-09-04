/*
 * Copyright (C) 2010-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.dedup;

import io.nut.base.util.concurrent.actor.ActorHub;
import io.nut.headless.image.hash.ImageClusterer;
import io.nut.headless.io.virtual.VirtualFile;
import java.io.IOException;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.commons.compress.archivers.ArchiveException;

/**
 *
 * @author franci
 */
public class FileComparatorByImage implements Comparator<VirtualFile>
{
    private final ImageClusterer imageClusterer;
    private final ActorHub hive;
    public FileComparatorByImage(int size, int maxPixelDiff, int maxFailures, double ratioDelta, ActorHub hive)
    {
        this.imageClusterer = new ImageClusterer(size, maxPixelDiff, maxFailures, ratioDelta);
        this.hive = hive;

    }

    @Override
    public int compare(VirtualFile a, VirtualFile b)
    {
        try
        {
            if(a.equals(b))
            {
                return 0;
            }
            Future<Integer> fa = hive.submit(()->
            {
                try
                {
                    return imageClusterer.add(a).id;
                }
                catch (IOException | ArchiveException ex)
                {
                    return -1;
                }
            });
            
            Future<Integer> fb = hive.submit(()->
            {
                try
                {
                    return imageClusterer.add(b).id;
                }
                catch (IOException | ArchiveException ex)
                {
                    return -1;
                }
            });
            
            return Integer.compare(fa.get(), fb.get());
        }
        catch (InterruptedException | ExecutionException ex)
        {
            System.getLogger(FileComparatorByImage.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return 0;
    }

}
