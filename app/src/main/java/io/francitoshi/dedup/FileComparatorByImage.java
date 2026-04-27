/*
 *  FileComparatorByImage.java
 *
 *  Copyright (C) 2010-2026 francitoshi@gmail.com
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

import io.nut.base.util.concurrent.hive.Hive;
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
    private final Hive hive;
    public FileComparatorByImage(int size, int maxPixelDiff, int maxFailures, double ratioDelta, Hive hive)
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
            Future<Integer> fa = hive.async(()->
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
            
            Future<Integer> fb = hive.async(()->
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
