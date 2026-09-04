/*
 * Copyright (C) 2009-2026 francitoshi@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 * See LICENSE file in the project root for full license text.
 */
package io.francitoshi.dedup;

import io.francitoshi.dedup.showgroup.ConsoleShowGroup;
import io.francitoshi.dedup.showgroup.ImageShowGroup;
import io.francitoshi.dedup.showgroup.ShowGroup;
import io.nut.headless.io.NameFileFilter;
import io.nut.base.io.FileUtils;
import io.nut.base.logging.VerboseHandler;
import io.nut.base.options.ArrayStringOption;
import io.nut.base.options.BooleanOption;
import io.nut.base.options.InvalidOptionException;
import io.nut.base.options.InvalidOptionParameterException;
import io.nut.base.options.MissingOptionParameterException;
import io.nut.base.options.NumberOption;
import io.nut.base.options.OptionParser;
import io.nut.base.options.SizeOption;
import io.nut.base.resources.I18n;
import io.nut.base.util.Concats;
import io.nut.base.util.SizeUnits;
import io.nut.base.util.Utils;
import io.nut.base.util.concurrent.actor.ActorHub;
import io.nut.headless.imageio.ImageFormat;
import io.nut.headless.io.ForEachFileOptions;
import io.nut.headless.io.virtual.VirtualFile;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main
{
    static final String DEDUP = "dedup";
    static final String COPYRIGHT = "Copyright (C) 2009-2026 francitoshi@gmail.com";
    static final String VER = Utils.firstNonNull(Main.class.getPackage().getImplementationVersion(), "[dev]");
    static final String VERSION = DEDUP + " v" + VER;
    static final String LICENSE_TXT;
    static final String HELP_TXT;
    static final String EXAMPLES_TXT;
    static 
    {
        I18n i18n = I18n.of(Main.class);
        
        HELP_TXT = i18n.resolveResource("help", "").replace("%FILE_PATH_SEPARATOR%",File.pathSeparator);
        LICENSE_TXT = i18n.resolveResource("license", "").replace("$COPYRIGHT$", COPYRIGHT);
        EXAMPLES_TXT = i18n.resolveResource("examples", "").replace("%FILE_PATH_SEPARATOR%",File.pathSeparator);
    }
     
    /**
     * @param args the command line arguments
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws InterruptedException, IOException
    {
        if (args.length < 1)
        {
            System.out.println(HELP_TXT);
            return;
        }
        int queueSize = 10000;
        int verboseLevel = 0;

        SizeUnits sizeParser = SizeUnits.getBinaryInstance();
        OptionParser options = new OptionParser();

        BooleanOption verbose = options.add(new BooleanOption('v', "verbose"));
        BooleanOption verboseLogger = options.add(new BooleanOption('V',"verbose-logger"));
        BooleanOption license = options.add(new BooleanOption('L', "license"));
        BooleanOption delete = options.add(new BooleanOption('d', "delete"));
        ArrayStringOption deleteAuto = options.add(new ArrayStringOption("delete-auto", File.pathSeparatorChar));
        BooleanOption delete1plus = options.add(new BooleanOption("delete-1+"));
        BooleanOption noempty = options.add(new BooleanOption('n', "noempty"));
        BooleanOption symlinks = options.add(new BooleanOption('s', "symlinks"));

        SizeOption minSize = options.add(new SizeOption('m', "min-size"));
        SizeOption maxSize = options.add(new SizeOption('M', "max-size"));
        SizeOption minWasted = options.add(new SizeOption('w', "min-wasted"));
        BooleanOption optSize = options.add(new BooleanOption('S', "size"));
        BooleanOption optNoHide = options.add(new BooleanOption('H', "nohide"));
        BooleanOption optAbsPath = options.add(new BooleanOption('A', "absolute-path"));

        BooleanOption unique = options.add(new BooleanOption("unique"));
        NumberOption count = options.add(new NumberOption("count"));
        NumberOption minCount = options.add(new NumberOption('c', "min-count"));
        NumberOption maxCount = options.add(new NumberOption('C', "max-count"));

        BooleanOption excRcIde = options.add(new BooleanOption('X',"exclude-rc-ide"));
        
        BooleanOption excRc = options.add(new BooleanOption("exclude-rc"));
        BooleanOption excSvn = options.add(new BooleanOption("exclude-svn"));
        BooleanOption excCvs = options.add(new BooleanOption("exclude-cvs"));
        BooleanOption excHg = options.add(new BooleanOption("exclude-hg"));
        BooleanOption excGit = options.add(new BooleanOption("exclude-git"));

        BooleanOption excIde = options.add(new BooleanOption("exclude-ide"));
        BooleanOption excIdea= options.add(new BooleanOption("exclude-idea"));
        BooleanOption excNb = options.add(new BooleanOption("exclude-nb"));
        BooleanOption excEclipse = options.add(new BooleanOption("exclude-eclipse"));
        
        ArrayStringOption excludeDirName = options.add(new ArrayStringOption("exclude-dir", File.pathSeparatorChar));
        ArrayStringOption excludeFileName = options.add(new ArrayStringOption("exclude-file", File.pathSeparatorChar));

        BooleanOption noautoexclude = options.add(new BooleanOption("noautoexclude"));
        ArrayStringOption exclude = options.add(new ArrayStringOption("exclude", File.pathSeparatorChar));

        ArrayStringOption focusPath = options.add(new ArrayStringOption('f', "focus", File.pathSeparatorChar));
        ArrayStringOption focusDirName = options.add(new ArrayStringOption("focus-dir", File.pathSeparatorChar));
        ArrayStringOption focusFileName = options.add(new ArrayStringOption("focus-file", File.pathSeparatorChar));
        BooleanOption optZip = options.add(new BooleanOption('z', "zip"));
        BooleanOption optZipOnly = options.add(new BooleanOption('Z', "zip-only"));

        BooleanOption optByName = options.add(new BooleanOption("byname"));
        BooleanOption optByIName = options.add(new BooleanOption("byiname"));
        BooleanOption optByImage = options.add(new BooleanOption("byimage"));
        NumberOption optByImageSize = options.add(new NumberOption("byimage-size"));
        ArrayStringOption dirName = options.add(new ArrayStringOption("dir", File.pathSeparatorChar));
        ArrayStringOption fileName = options.add(new ArrayStringOption("file", File.pathSeparatorChar));
        BooleanOption regex = options.add(new BooleanOption('e',"regex"));
        BooleanOption wildcard = options.add(new BooleanOption("wildcard"));
        BooleanOption bug = options.add(new BooleanOption("bug"));
        BooleanOption bugFix = options.add(new BooleanOption("bug-fix"));
        BooleanOption optLowMem = options.add(new BooleanOption("lowmem"));

        BooleanOption version = options.add(new BooleanOption("version"));
        BooleanOption help = options.add(new BooleanOption('h', "help"));
        BooleanOption examples = options.add(new BooleanOption("examples"));

        String[] fileNames;
        try
        {
            fileNames = options.parse(args);
        }
        catch (InvalidOptionException ex)
        {
            System.err.println(ex);
            System.err.println(DEDUP + ": Try --help for more information");
            return;
        }

        if (help.isUsed())
        {
            System.out.println(HELP_TXT);
            return;
        }
        if (version.isUsed())
        {
            System.out.println(VERSION);
            return;
        }
        if (license.isUsed())
        {
            System.out.println(LICENSE_TXT);
            return;
        }
        if (examples.isUsed())
        {
            System.out.println(EXAMPLES_TXT);
            return;
        }
        if (verbose.isUsed())
        {
            verboseLevel = verbose.getCount();
        }

        VerboseHandler vh = verboseLogger.isUsed() ? new VerboseHandler(System.err, true, new SimpleFormatter()) : new VerboseHandler(System.err, true, "dedup: ");
        VerboseHandler.register(verboseLevel, vh, ConsoleHandler.class);

        Logger logger = Logger.getLogger(Main.class.getName());
        if (logger.isLoggable(Level.CONFIG))
        {
            logger.log(Level.CONFIG, "{0}.version={1}", new String[]{DEDUP, VER});
            logger.log(Level.CONFIG, "logger.level={0}", vh.getLevel().getName());
            options.log();
            vh.flush();
        }

        boolean minSizeUsed = false;
        boolean maxSizeUsed = false;
        boolean minWastedUsed = false;
        long minSizeValue = 0;
        long maxSizeValue = Long.MAX_VALUE;
        long minWastedValue = Long.MAX_VALUE;
            
        try(ActorHub hive = new ActorHub(ActorHub.CORES*4, ActorHub.CORES*8, 30_000, false))
        {
            if (minSize.isUsed())
            {
                minSizeValue = minSize.longValue();
                minSizeUsed = true;
            }
            if (maxSize.isUsed())
            {
                maxSizeValue = maxSize.longValue();
                maxSizeUsed = true;
            }
            if (minWasted.isUsed())
            {
                minWastedValue = minWasted.longValue();
                minWastedUsed = true;
            }

            if (fileNames.length == 0)
            {
                System.err.println(DEDUP + ": no directories specified");
                return;
            }

            File[] files = FileUtils.toFileArray(fileNames);
            File[] autoDeleteFiles = new File[0];
            if (deleteAuto.isUsed())
            {
                autoDeleteFiles = FileUtils.getAbsoluteFile(FileUtils.toFileArray(deleteAuto.getValues()));
                if (autoDeleteFiles.length > 0)
                {
                    files = Concats.cat(files, autoDeleteFiles);
                }
            }

            DeDupOptions opt = new DeDupOptions();

            opt.setHidden(!optNoHide.isUsed());
            opt.setSymlinks(symlinks.isUsed());

            if (noempty.isUsed())
            {
                opt.setMinSize(1);
            }
            if (minSizeUsed)
            {
                opt.setMinSize(minSizeValue);
            }
            if (maxSizeUsed)
            {
                opt.setMaxSize(maxSizeValue);
            }
            if (minWastedUsed)
            {
                opt.setMinWasted(minWastedValue);
            }
            if (noautoexclude.isUsed())
            {
                opt.setAutoOmit(false);
            }
            if (exclude.isUsed())
            {
                String[] paths = exclude.getValues();
                for (String item : paths)
                {
                    opt.addOmitedPath(new File(item));
                }
            }
            boolean useRegEx = regex.isUsed() && (!wildcard.isUsed() || regex.getLastUsed() > wildcard.getLastUsed());

            boolean fixBugs = bugFix.isUsed();
            boolean bugs = fixBugs || bug.isUsed();

            excludeRc(opt, excRcIde, excRc, excSvn, excCvs, excHg, excGit, excIde, excIdea, excNb, excEclipse, verboseLevel);
            excludeDirAndFile(opt, excludeDirName, excludeFileName, useRegEx);
            focusPathDirFile(opt, focusPath, focusDirName, focusFileName, useRegEx);
            dirFile(opt, dirName, fileName, useRegEx);

            if (optZip.isUsed())
            {
                opt.setZip(true);
                opt.setJar(true);
            }
            if (optZipOnly.isUsed())
            {
                opt.setZip(true);
                opt.setJar(true);
                opt.setOnlyPacked(true);
            }
//            if(optByHash.isUsed())
//            {
//                opt.setByHash(true);
//            }
//
//            if(optIByPath.isUsed()||optByPath.isUsed())
//            {
//                opt.setByName(true,0);
//                opt.setByNameIgnoreCase(optIByName.isUsed());
//            }
//            else
            if(optByIName.isUsed() || optByName.isUsed())
            {
                opt.setComparators
                (
                    new FileComparatorByName(true, optByIName.isUsed()),
                    new FileComparatorByName(false, optByIName.isUsed())
                );
            }
            else if(optByImage.isUsed())
            {
                int size = optByImageSize.intValue(32);
                FileComparatorByImage imageCmp = new FileComparatorByImage(size, 48, size*size/8, 0.1, hive);
                opt.setComparators(imageCmp,imageCmp);
                opt.addAllowedFileName(ImageFormat.getImageFileFilter());
            }

            // ignore groups of 1 unless it specified by options
            opt.setMinCount(2);
            countFilter(opt, unique, count, minCount, maxCount, verboseLevel);

            if (logger.isLoggable(Level.CONFIG))
            {
                for (int i = 0; i < fileNames.length; i++)
                {
                    logger.log(Level.CONFIG, "paths[{0}]={1}", new Object[]{i, fileNames[i]});
                }
                vh.flush();
            }

            DeDupActor findTask = new DeDupActor(hive, files, bugs, queueSize, opt);
            if(optLowMem.isUsed())
            {
                findTask.setLowmem(true);
            }

            new Thread(findTask).start();

            if (bugs)
            {
                showBugs(findTask.getBugIterable(), fixBugs, vh);
            }
            showGroups(opt, findTask.getGroupsIterable(), delete.isUsed(), delete1plus.isUsed(),(optSize.isUsed() ? sizeParser : null), autoDeleteFiles, vh, optAbsPath.isUsed(), optByImage.isUsed());

        }
        catch (MissingOptionParameterException | InvalidOptionParameterException | NumberFormatException ex)
        {
            System.err.println(DEDUP + ":" + ex.getMessage());
        }
    }

    private static void excludeDirAndFile(ForEachFileOptions opt, ArrayStringOption excludeDirName, ArrayStringOption excludeFileName, boolean useRegEx)
    {
        if (excludeDirName.isUsed())
        {
            String[] paths = excludeDirName.getValues();
            for (String item : paths)
            {
                opt.addOmitedDirName(item, !useRegEx);
            }
        }
        if (excludeFileName.isUsed())
        {
            String[] paths = excludeFileName.getValues();
            for (String item : paths)
            {
                opt.addOmitedFileName(item, !useRegEx);
            }
        }
    }

    private static void focusPathDirFile(DeDupOptions opt, ArrayStringOption focusPath, ArrayStringOption focusDirName, ArrayStringOption focusFileName, boolean useRegEx)
    {
        if (focusPath.isUsed())
        {
            String[] paths = focusPath.getValues();
            for (String item : paths)
            {
                opt.addFocusPath(item);
            }
        }
        if (focusDirName.isUsed())
        {
            String[] dirName = focusDirName.getValues();
            for (String item : dirName)
            {
                opt.addFocusDir(item, !useRegEx);
            }
        }
        if (focusFileName.isUsed())
        {
            String[] fileName = focusFileName.getValues();
            for (String item : fileName)
            {
                opt.addFocusFile(item, !useRegEx);
            }
        }
    }
    private static void dirFile(DeDupOptions opt, ArrayStringOption dirName, ArrayStringOption fileName, boolean useRegEx)
    {
        if (dirName.isUsed())
        {
            String[] names = dirName.getValues();
            for (String item : names)
            {
                opt.addDirName(item, !useRegEx);
            }
        }
        if (fileName.isUsed())
        {
            String[] names = fileName.getValues();
            for (String item : names)
            {
                opt.addFileName(item, !useRegEx);
            }
        }
    }

    private static void excludeRc(ForEachFileOptions opt, BooleanOption all, BooleanOption allRc, BooleanOption svn, BooleanOption cvs, BooleanOption hg, BooleanOption git, BooleanOption allIde, BooleanOption idea, BooleanOption nb, BooleanOption eclipse, int verboseLevel)
    {
        if(all.isUsed() || allRc.isUsed() || svn.isUsed())
        {
            opt.addOmitedDirName(".svn");
            if (verboseLevel > 0)
            {
                System.out.println(DEDUP + ": excluded subversion files");
            }

        }
        if(all.isUsed() || allRc.isUsed() || cvs.isUsed())
        {
            opt.addOmitedDirName("CVS");
            if (verboseLevel > 0)
            {
                System.out.println(DEDUP + ": excluded CVS files");
            }
        }
        if(all.isUsed() || allRc.isUsed() || hg.isUsed())
        {
            opt.addOmitedDirName(".hg");
            opt.addOmitedFileName(".hgignore",new NameFileFilter.Rule[]{NameFileFilter.getDir(".hg", true)});
            if (verboseLevel > 0)
            {
                System.out.println(DEDUP + ": excluded mercurial files");
            }
        }
        if(all.isUsed() || allRc.isUsed() || git.isUsed())
        {
            opt.addOmitedDirName(".git");
            opt.addOmitedFileName(".gitignore",new NameFileFilter.Rule[]{NameFileFilter.getDir(".git", true)});
            if (verboseLevel > 0)
            {
                System.out.println(DEDUP + ": excluded git files");
            }
        }
        if(all.isUsed() || allIde.isUsed() || idea.isUsed())
        {
            opt.addOmitedDirName(".idea");
            opt.addOmitedFileName("*.iml",true, new NameFileFilter.Rule[]{NameFileFilter.getDir(".idea", true)});
            opt.addOmitedDirName("out",new NameFileFilter.Rule[]{NameFileFilter.getDir(".idea", true)});
            if (verboseLevel > 0)
            {
                System.out.println(DEDUP + ": excluded Intellij IDEA files");
            }
        }
        if(all.isUsed() || allIde.isUsed() || nb.isUsed())
        {
            opt.addOmitedDirName("nbproject",new NameFileFilter.Rule[]{NameFileFilter.getFile("build.xml", true)});
            opt.addOmitedFileName("build.xml",new NameFileFilter.Rule[]{NameFileFilter.getDir("nbproject", true)});
            opt.addOmitedDirName("dist",new NameFileFilter.Rule[]{NameFileFilter.getFile("build.xml", true),NameFileFilter.getDir("nbproject", true)});
            opt.addOmitedDirName("build",new NameFileFilter.Rule[]{NameFileFilter.getFile("build.xml", true),NameFileFilter.getDir("nbproject", true)});
            if (verboseLevel > 0)
            {
                System.out.println(DEDUP + ": excluded git files");
            }
        }
        if(all.isUsed() || allIde.isUsed() || eclipse.isUsed())
        {
            opt.addOmitedFileName(".classpath",new NameFileFilter.Rule[]{NameFileFilter.getFile(".project", true)});
            opt.addOmitedFileName(".project",new NameFileFilter.Rule[]{NameFileFilter.getFile(".classpath", true)});
            opt.addOmitedDirName(".settings",new NameFileFilter.Rule[]{NameFileFilter.getFile(".classpath", true),NameFileFilter.getFile(".project", true),});
            if (verboseLevel > 0)
            {
                System.out.println(DEDUP + ": excluded git files");
            }
        }
    }

    private static void countFilter(DeDupOptions opt, BooleanOption unique, NumberOption count, NumberOption minCount, NumberOption maxCount, int verboseLevel) throws MissingOptionParameterException, InvalidOptionParameterException
    {
        int lastUsed = -1;
        if (unique.isUsed())
        {
            opt.setMinCount(1);
            opt.setMaxCount(1);
            lastUsed = unique.getLastUsed();
        }
        if (count.isUsed() && count.getLastUsed() > lastUsed)
        {
            int num = count.intValue();
            if (num > 0)
            {
                opt.setMinCount(num);
                opt.setMaxCount(num);
            }
            lastUsed = count.getLastUsed();
        }
        if (minCount.isUsed() && minCount.getLastUsed() > lastUsed)
        {
            int num = minCount.intValue();
            if (num > 0)
            {
                opt.setMinCount(num);
            }
        }
        if (maxCount.isUsed() && maxCount.getLastUsed() > lastUsed)
        {
            int num = maxCount.intValue();
            if (num > 0)
            {
                opt.setMaxCount(num);
            }
        }
        if (verboseLevel > 0)
        {
            if (opt.getMinCount() == opt.getMaxCount())
            {
                System.out.println(DEDUP + ": exactly " + opt.getMinCount() + " occurrence" + ((opt.getMinCount() == 1) ? "" : "s"));
            }
            else if (opt.getMaxCount() != Integer.MAX_VALUE)
            {
                System.out.println(DEDUP + ": between " + opt.getMinCount() + " and " + opt.getMaxCount() + " occurrences");
            }
            else if (opt.getMinCount() != 2)
            {
                System.out.println(DEDUP + ": at least " + opt.getMinCount() + " occurrence" + ((opt.getMinCount() == 1) ? "" : "s"));
            }
        }

    }

    private static void showBugs(Iterable<File> bugList, boolean fix, VerboseHandler vh)
    {
        //METER NUEVA OPCIÓN Y COLA PARA MOSTRAR LOS LINKS SIN DESTINO
        //        EVITANDO ASÍ SUPERPONERSE CON LAS PREGUNTAS

        try
        {
            Scanner sc = new Scanner(System.in);
            for (File bug : bugList)
            {
                vh.flush();
                vh.lock();
                String line;
                try
                {
                    System.out.println();
                    System.out.println("bug:" + bug.toString());
                    if(!fix)
                        continue;
                    
                    System.out.print("fix?[y/N]");
                    line = sc.nextLine();
                }
                finally
                {
                    vh.unlock();
                }
                if (line.equalsIgnoreCase("y"))
                {
                    NormalizeFile normalize = new NormalizeFile(bug);
                    String name = normalize.normalize();
                    if(normalize.getValue()!=0)
                    {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "could not be fixed");
                        Logger.getLogger(Main.class.getName()).log(Level.WARNING, normalize.getErrorMessage());
                    }
                    else
                    {
                        Logger.getLogger(Main.class.getName()).log(Level.INFO, "fixed: {0}",name);
                    }
                }
            }
        }
        catch (InterruptedException | IOException ex)
        {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void showGroups(DeDupOptions opt, Iterable<VirtualFile[]> groupsList, boolean delete, boolean delete1Plus, SizeUnits units, File[] autoDelete, VerboseHandler vh, boolean absPath, boolean byimage)
    {
        int groupId = 0;
        int deleteMin = opt.getMinCount();
        
        ShowGroup sg = (delete && byimage && !GraphicsEnvironment.isHeadless())
                ? new ImageShowGroup(units, absPath, delete, deleteMin, autoDelete, delete1Plus)
                : new ConsoleShowGroup(units, absPath, delete, deleteMin, autoDelete, delete1Plus);

        for (VirtualFile[] group : groupsList)
        {
            if (group.length >= opt.getMinCount() && group.length <= opt.getMaxCount())
            {
                vh.flush();
                vh.lock();
                try
                {
                    VERIFY(group);
                    sg.showOneGroup(groupId, group);
                }
                catch (IOException ex)
                {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
                finally
                {
                    vh.unlock();
                }
                groupId++;
            }
        }
        sg.close();
    }

    private static void VERIFY(VirtualFile[] group)
    {
        try
        {
            HashMap<String,VirtualFile> items = new HashMap<>(group.length);
            for (int i = 0; i < group.length; i++)
            {
                String a = group[i].getCanonicalPath();
                VirtualFile dup = items.put(a,group[i]);
                if(dup!=null)
                {
                    String b = dup.getCanonicalPath();
                    if(a.equals(b))
                    {
                        System.err.printf("YOU HAVE FOUND A BUG: THE SAME FILE REPORTED TWICE\n");
                        System.err.printf("A=%s\n",group[i].toString());
                        System.err.printf("B=%s\n",dup.toString());
                    }
                }
            }
        }
        catch (IOException ex)
        {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
