/*
 *  DeDupOptions.java
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

import io.nut.headless.io.ForEachFileOptions;
import io.nut.headless.io.NameFileFilter;
import io.nut.headless.io.virtual.VirtualFile;
import java.io.File;
import java.io.FileFilter;
import java.util.Comparator;
import java.util.HashSet;

/**
 *
 * @author franci
 */
public class DeDupOptions extends ForEachFileOptions
{
    private int minCount;
    private int maxCount;
    private long minWasted;
    private boolean eager;

    private boolean hasFocusPaths;
    private final HashSet<File> focusPaths;
    private boolean hasFocusDir;
    private final HashSet<FileFilter> focusDir;
    private boolean hasFocusFile;
    private final HashSet<FileFilter> focusFile;

    private boolean hasDirName;
    private final HashSet<FileFilter> dirName;
    private boolean hasFileName;
    private final HashSet<FileFilter> fileName;

    public final static Comparator<VirtualFile> BYHASH_HALF_COMPARATOR = new FileComparatorByHash(true);
    public final static Comparator<VirtualFile> BYHASH_FULL_COMPARATOR = new FileComparatorByHash(false);
    public final static Comparator<VirtualFile> DEFAULT_HALF_COMPARATOR = BYHASH_HALF_COMPARATOR;
    public final static Comparator<VirtualFile> DEFAULT_FULL_COMPARATOR = BYHASH_FULL_COMPARATOR;
        
    private Comparator<VirtualFile> halfCmp;
    private Comparator<VirtualFile> fullCmp;

    public DeDupOptions()
    {
        super();
        minCount = 0;
        maxCount = Integer.MAX_VALUE;
        eager    = false;

        hasFocusPaths = false;
        focusPaths = new HashSet<>();
        hasFocusDir = false;
        focusDir = new HashSet<>();
        hasFocusFile= false;
        focusFile= new HashSet<>();

        hasDirName = false;
        dirName= new HashSet<>();//at least one of the parent directories must match a rule for every file
        hasFileName = false;
        fileName = new HashSet<>();// every file must match a rule
        halfCmp = DEFAULT_HALF_COMPARATOR;
        fullCmp = DEFAULT_FULL_COMPARATOR;
    }

    public DeDupOptions(ForEachFileOptions val)
    {
        super(val);
        minCount = 0;
        maxCount = Integer.MAX_VALUE;
        eager    = false;

        hasFocusPaths = false;
        focusPaths = new HashSet<>();
        hasFocusDir = false;
        focusDir = new HashSet<>();
        hasFocusFile= false;
        focusFile= new HashSet<>();

        hasDirName = false;
        dirName= new HashSet<>();//at least one of the parent directories must match a rule for every file
        hasFileName = false;
        fileName = new HashSet<>();// every file must match a rule
        halfCmp = DEFAULT_HALF_COMPARATOR;
        fullCmp = DEFAULT_FULL_COMPARATOR;
    }

    public DeDupOptions(DeDupOptions val)
    {
        super(val);
        minCount = val.minCount;
        maxCount = val.maxCount;
        minWasted= val.minWasted;
        eager    = val.eager;

        hasFocusPaths  = val.hasFocusPaths;
        focusPaths     = new HashSet<>(val.focusPaths);
        hasFocusDir    = val.hasFocusDir;
        focusDir       = new HashSet<>(val.focusDir);
        hasFocusFile   = val.hasFocusFile;
        focusFile      = new HashSet<>(val.focusFile);

        hasDirName = val.hasDirName;
        dirName= new HashSet<>(val.dirName);
        hasFileName = val.hasFileName;
        fileName = new HashSet<>(val.fileName);
        halfCmp = val.halfCmp;
        fullCmp = val.fullCmp;
    }

    public int getMaxCount()
    {
        return maxCount;
    }

    public void setMaxCount(int maxCount)
    {
        this.maxCount = maxCount;
    }

    public int getMinCount()
    {
        return minCount;
    }

    public void setMinCount(int minCount)
    {
        this.minCount = minCount;
    }

    public long getMinWasted()
    {
        return minWasted;
    }

    public void setMinWasted(long minWasted)
    {
        this.minWasted = minWasted;
    }

    public boolean isEager()
    {
        return eager;
    }

    public void setEager(boolean eager)
    {
        this.eager = eager;
    }
    
    
    public void addFocusPath(File path)
    {
        focusPaths.add(path);
        hasFocusPaths = true;
    }
    public void addFocusPath(String path)
    {
        addFocusPath(new File(path));
    }
    public void addFocusDir(String name,boolean wildcard)
    {
        FileFilter filter = wildcard?NameFileFilter.getWildCardInstance(name):NameFileFilter.getRegExInstance(name);
        focusDir.add(filter);
        hasFocusDir = true;
    }
    public void addFocusFile(String name,boolean wildcard)
    {
        FileFilter filter = wildcard?NameFileFilter.getWildCardInstance(name):NameFileFilter.getRegExInstance(name);
        focusFile.add(filter);
        hasFocusFile = true;
    }

    public void addDirName(String name,boolean wildcard)
    {
        FileFilter filter = wildcard?NameFileFilter.getWildCardInstance(name):NameFileFilter.getRegExInstance(name);
        dirName.add(filter);
        hasDirName = true;
    }
    public void addFileName(String name,boolean wildcard)
    {
        FileFilter filter = wildcard?NameFileFilter.getWildCardInstance(name):NameFileFilter.getRegExInstance(name);
        fileName.add(filter);
        hasFileName = true;
    }

    public File[] getFocusPaths()
    {
        return focusPaths.toArray(File[]::new);
    }

    public FileFilter[] getFocusDirs()
    {
        return focusDir.toArray(FileFilter[]::new);
    }

    public FileFilter[] getFocusFiles()
    {
        return focusFile.toArray(FileFilter[]::new);
    }

    FileFilter[] getDirNames()
    {
        return dirName.toArray(FileFilter[]::new);
    }

    FileFilter[] getFileNames()
    {
        return fileName.toArray(FileFilter[]::new);
    }

    public void setComparators(Comparator<VirtualFile> half, Comparator<VirtualFile> full)
    {
        this.halfCmp = half;
        this.fullCmp = full;
    }

    public Comparator<VirtualFile> getFullCmp()
    {
        return fullCmp;
    }

    public Comparator<VirtualFile> getHalfCmp()
    {
        return halfCmp;
    }
}
