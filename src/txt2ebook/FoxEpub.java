/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package txt2ebook;

/**
 *
 * @author guanli
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class FoxEpub {

    private FileOutputStream fos;
    private ZipOutputStream zos;
    String SavePath = "/xxx.epub";
    String TmpDir = "/";
    boolean isEpub = true;
    String BookUUID = UUID.randomUUID().toString();
    String BookName = "狐狸之书";
    String BookCreator = "爱尔兰之狐";
    String DefNameNoExt = "FoxMake"; //默认文件名
    String ImageExt = "png";
    String ImageMetaType = "image/png";
    ArrayList<HashMap<String, Object>> Chapter = new ArrayList<HashMap<String, Object>>(200); //章节结构:1:ID 2:Title
    int ChapterCount = 0; //章节数
    int ChapterID = 100; //章节ID

    FoxEpub(String inBookName, String inSavePath) {
        this.BookName = inBookName;
        this.SavePath = inSavePath;
        if (inSavePath.toLowerCase().endsWith(".epub")) {
            this.isEpub = true;
        } else {
            this.isEpub = false;
        }
        File saveF = new File(this.SavePath);
        String inSaveDir = saveF.getParent();
        this.TmpDir = inSaveDir + File.separator + "FoxEpub_" + System.currentTimeMillis();

        if (isEpub) {
            if (saveF.exists()) { // 文件存在
                saveF.renameTo(new File(this.SavePath + System.currentTimeMillis()));
            }
            try {
                fos = new FileOutputStream(this.SavePath);
            } catch (FileNotFoundException ex) {
                System.out.println("创建epub文件错误: " + ex.toString());
            }
            zos = new ZipOutputStream(fos);
        } else { // mobi
            File td = new File(this.TmpDir);
            if (!td.exists()) {
                new File(this.TmpDir + File.separator + "html").mkdirs();
                new File(this.TmpDir + File.separator + "META-INF").mkdirs();
            } else {
                System.out.println("错误:目录存在: " + this.TmpDir);
            }
        }
    }

    public void AddChapter(String Title, String Content, int iPageID) {
        if (iPageID < 0) {
            ++this.ChapterID;
        } else {
            this.ChapterID = iPageID;
        }

        HashMap<String, Object> cc = new HashMap<String, Object>();
        cc.put("id", this.ChapterID);
        cc.put("name", Title);
        Chapter.add(cc);

        this._CreateChapterHTML(Title, Content, this.ChapterID); //写入文件
    }

    public void SaveTo() {
        this._CreateIndexHTM();
        this._CreateNCX();
        this._CreateOPF();
        this._CreateEpubMiscFiles();

        if (isEpub) {
            try {
                zos.close();
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else { // 生成mobi
            try {
                Process cmd = Runtime.getRuntime().exec("kindlegen " + DefNameNoExt + ".opf", null, new File(TmpDir));
                cmd.waitFor();
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            File tmpF = new File(TmpDir + File.separator + DefNameNoExt + ".mobi");
            if (tmpF.exists() && tmpF.length() > 555) {
                tmpF.renameTo(new File(SavePath));
                DeleteFolder(TmpDir); // 移除临时目录
            }
        }
    }

    private void _CreateNCX() { //生成NCX文件
        StringBuffer NCXList = new StringBuffer(4096);
        int DisOrder = 1; //初始 顺序, 根据下面的playOrder数据

        HashMap<String, Object> mm;
        int nowID = 0;
        String nowTitle = "";
        Iterator<HashMap<String, Object>> itr = Chapter.iterator();
        while (itr.hasNext()) {
            mm = itr.next();
            nowID = (Integer) mm.get("id");
            nowTitle = (String) mm.get("name");
            ++DisOrder;
            NCXList.append("\t<navPoint id=\"").append(nowID)
                    .append("\" playOrder=\"").append(DisOrder)
                    .append("\"><navLabel><text>").append(nowTitle)
                    .append("</text></navLabel><content src=\"html/").append(nowID)
                    .append(".html\" /></navPoint>\n");
        }

        StringBuffer XML = new StringBuffer(4096);
        XML.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE ncx PUBLIC \"-//NISO//DTD ncx 2005-1//EN\" \"http://www.daisy.org/z3986/2005/ncx-2005-1.dtd\">\n<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\" xml:lang=\"zh-cn\">\n<head>\n\t<meta name=\"dtb:uid\" content=\"")
                .append(BookUUID).append("\"/>\n\t<meta name=\"dtb:depth\" content=\"1\"/>\n\t<meta name=\"dtb:totalPageCount\" content=\"0\"/>\n\t<meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n\t<meta name=\"dtb:generator\" content=\"")
                .append(BookCreator).append("\"/>\n</head>\n<docTitle><text>")
                .append(BookName).append("</text></docTitle>\n<docAuthor><text>")
                .append(BookCreator).append("</text></docAuthor>\n<navMap>\n\t<navPoint id=\"toc\" playOrder=\"1\"><navLabel><text>目录:")
                .append(BookName).append("</text></navLabel><content src=\"").append(DefNameNoExt).append(".htm\"/></navPoint>\n")
                .append(NCXList).append("\n</navMap></ncx>\n");
        if (isEpub) {
            this.addTextToZip(DefNameNoExt + ".ncx", XML.toString());
        } else {
            createTxtFile(new File(this.TmpDir + File.separator + DefNameNoExt + ".ncx"), XML.toString());
        }
    }

    private void _CreateOPF() { //生成OPF文件
        String AddXMetaData = "";
        StringBuffer NowHTMLMenifest = new StringBuffer(4096);
        StringBuffer NowHTMLSpine = new StringBuffer(4096);

        HashMap<String, Object> mm;
        int nowID = 0;
//        String nowTitle = "";
        Iterator<HashMap<String, Object>> itr = Chapter.iterator();
        while (itr.hasNext()) {
            mm = itr.next();
            nowID = (Integer) mm.get("id");
//            nowTitle = (String)mm.get("name");

            NowHTMLMenifest.append("\t<item id=\"page").append(nowID).append("\" media-type=\"application/xhtml+xml\" href=\"html/").append(nowID).append(".html\" />\n");
            NowHTMLSpine.append("\t<itemref idref=\"page").append(nowID).append("\" />\n");
        }

        // 图片列表加载这里
        String NowImgMenifest = "";
        if (!isEpub) {
            AddXMetaData = "\t<x-metadata><output encoding=\"utf-8\"></output></x-metadata>\n";
        }

        StringBuffer XML = new StringBuffer(4096);
        XML.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"FoxUUID\">\n<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:opf=\"http://www.idpf.org/2007/opf\">\n\t<dc:title>")
                .append(BookName).append("</dc:title>\n\t<dc:identifier opf:scheme=\"uuid\" id=\"FoxUUID\">").append(BookUUID)
                .append("</dc:identifier>\n\t<dc:creator>").append(BookCreator).append("</dc:creator>\n\t<dc:publisher>")
                .append(BookCreator).append("</dc:publisher>\n\t<dc:language>zh-cn</dc:language>\n").append(AddXMetaData)
                .append("</metadata>\n\n\n<manifest>\n\t<item id=\"FoxNCX\" media-type=\"application/x-dtbncx+xml\" href=\"")
                .append(DefNameNoExt).append(".ncx\" />\n\t<item id=\"FoxIDX\" media-type=\"application/xhtml+xml\" href=\"")
                .append(DefNameNoExt).append(".htm\" />\n\n").append(NowHTMLMenifest).append("\n\n")
                .append(NowImgMenifest).append("\n</manifest>\n\n<spine toc=\"FoxNCX\">\n\t<itemref idref=\"FoxIDX\"/>\n\n\n")
                .append(NowHTMLSpine).append("\n</spine>\n\n\n<guide>\n\t<reference type=\"text\" title=\"正文\" href=\"")
                .append("html/").append(Chapter.get(0).get("id")).append(".html\"/>\n\t<reference type=\"toc\" title=\"目录\" href=\"")
                .append(DefNameNoExt).append(".htm\"/>\n</guide>\n\n</package>\n\n");
        if (isEpub) {
            this.addTextToZip(DefNameNoExt + ".opf", XML.toString());
        } else {
            createTxtFile(new File(this.TmpDir + File.separator + DefNameNoExt + ".opf"), XML.toString());
        }
    }

    private void _CreateEpubMiscFiles() { //生成 epub 必须文件 mimetype, container.xml
        StringBuffer XML = new StringBuffer(256);
        XML.append("<?xml version=\"1.0\"?>\n<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n\t<rootfiles>\n\t\t<rootfile full-path=\"")
                .append(this.DefNameNoExt).append(".opf")
                .append("\" media-type=\"application/oebps-package+xml\"/>\n\t</rootfiles>\n</container>\n");
        if (isEpub) {
            addMIMETYPE();
            addTextToZip("META-INF/container.xml", XML.toString());
        } else {
            createTxtFile(new File(this.TmpDir + File.separator + "mimetype"), "application/epub+zip");
            createTxtFile(new File(this.TmpDir + File.separator + "META-INF" + File.separator + "container.xml"), XML.toString());
        }
    }

    private void _CreateChapterHTML(String Title, String Content, int iPageID) { //生成章节页面
        StringBuffer HTML = new StringBuffer(20480);

        HTML.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"zh-CN\">\n<head>\n\t<title>")
                .append(Title)
                .append("</title>\n\t<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n\t<style type=\"text/css\">\n\t\th2,h3,h4{text-align:center;}\n\t\tp { text-indent: 2em; line-height: 0.5em; }\n\t</style>\n</head>\n<body>\n<h4>")
                .append(Title)
                .append("</h4>\n<div class=\"content\">\n\n\n")
                .append(Content)
                .append("\n\n\n</div>\n</body>\n</html>\n");

        if (isEpub) {
            this.addTextToZip("html" + File.separator + iPageID + ".html", HTML.toString());
        } else {
            createTxtFile(new File(this.TmpDir + File.separator + "html" + File.separator + iPageID + ".html"), HTML.toString());
        }
    }

    private void _CreateIndexHTM() { //生成索引页
        StringBuffer NowTOC = new StringBuffer(4096);

        HashMap<String, Object> mm;
        int nowID = 0;
        String nowTitle = "";
        Iterator<HashMap<String, Object>> itr = Chapter.iterator();
        while (itr.hasNext()) {
            mm = itr.next();
            nowID = (Integer) mm.get("id");
            nowTitle = (String) mm.get("name");
            NowTOC.append("<div><a href=\"html/").append(nowID).append(".html\">").append(nowTitle).append("</a></div>\n");
        }

        StringBuffer XML = new StringBuffer(4096);
        XML.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"zh-CN\">\n<head>\n\t<title>")
                .append(BookName).append("</title>\n\t<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n\t<style type=\"text/css\">h2,h3,h4{text-align:center;}</style>\n</head>\n<body>\n<h2>")
                .append(BookName).append("</h2>\n<div class=\"toc\">\n\n").append(NowTOC).append("\n\n</div>\n</body>\n</html>\n");
        if (isEpub) {
            this.addTextToZip(DefNameNoExt + ".htm", XML.toString());
        } else {
            createTxtFile(new File(this.TmpDir + File.separator + DefNameNoExt + ".htm"), XML.toString());
        }
    }

    public void addTextToZip(String saveName, String content) {
        try {
            byte[] b = content.getBytes("UTF-8");
            ZipEntry entry2 = new ZipEntry(saveName);
            zos.putNextEntry(entry2);
            zos.write(b, 0, b.length);
            zos.closeEntry();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addMIMETYPE() {
        byte[] b = "application/epub+zip".getBytes();
        ZipEntry entry = new ZipEntry("mimetype");
        entry.setMethod(0);
        entry.setSize(b.length);
        entry.setCompressedSize(b.length);
        CRC32 crc = new CRC32();
        crc.update(b);
        entry.setCrc(crc.getValue());
        try {
            zos.putNextEntry(entry);
            zos.write(b, 0, b.length);
            zos.closeEntry();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ★★★★★★★★★★★★★★★★★★下面是一般通用的★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
    
    public static void createTxtFile(File txtFile, String cc) { // 创建Txt文件
        try {
            txtFile.createNewFile();
            FileOutputStream outImgStream = new FileOutputStream(txtFile);
            outImgStream.write(cc.getBytes("UTF-8"));
            outImgStream.close();
        } catch (Exception e) {
            e.toString();
        }
    }

    /**
     * 根据路径删除指定的目录或文件，无论存在与否
     *
     * @param sPath 要删除的目录或文件
     * @return 删除成功返回 true，否则返回 false。
     */
    public boolean DeleteFolder(String sPath) {
        boolean flag = false;
        File file = new File(sPath);
        // 判断目录或文件是否存在  
        if (!file.exists()) {  // 不存在返回 false  
            return flag;
        } else {
            // 判断是否为文件  
            if (file.isFile()) {  // 为文件时调用删除文件方法  
                return deleteFile(sPath);
            } else {  // 为目录时调用删除目录方法  
                return deleteDirectory(sPath);
            }
        }
    }

    /**
     * 删除单个文件
     *
     * @param sPath 被删除文件的文件名
     * @return 单个文件删除成功返回true，否则返回false
     */
    public boolean deleteFile(String sPath) {
        boolean flag = false;
        File file = new File(sPath);
        // 路径为文件且不为空则进行删除  
        if (file.isFile() && file.exists()) {
            file.delete();
            flag = true;
        }
        return flag;
    }

    /**
     * 删除目录（文件夹）以及目录下的文件
     *
     * @param sPath 被删除目录的文件路径
     * @return 目录删除成功返回true，否则返回false
     */
    public boolean deleteDirectory(String sPath) {
        //如果sPath不以文件分隔符结尾，自动添加文件分隔符  
        if (!sPath.endsWith(File.separator)) {
            sPath = sPath + File.separator;
        }
        File dirFile = new File(sPath);
        //如果dir对应的文件不存在，或者不是一个目录，则退出  
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            return false;
        }
        boolean flag = true;
        //删除文件夹下的所有文件(包括子目录)  
        File[] files = dirFile.listFiles();
        for (int i = 0; i < files.length; i++) {
            //删除子文件  
            if (files[i].isFile()) {
                flag = deleteFile(files[i].getAbsolutePath());
                if (!flag) {
                    break;
                }
            } //删除子目录  
            else {
                flag = deleteDirectory(files[i].getAbsolutePath());
                if (!flag) {
                    break;
                }
            }
        }
        if (!flag) {
            return false;
        }
        //删除当前目录  
        if (dirFile.delete()) {
            return true;
        } else {
            return false;
        }
    }
/*
    public static void main(String[] args) {
//        FoxEpub oEpub = new FoxEpub("书名是我", "C:\\etc\\xxx.mobi");
        FoxEpub oEpub = new FoxEpub("书名是我", "C:\\etc\\xxx.epub");
        oEpub.AddChapter("第1章", "今天你当天嫩肤流口水司法会计地方<br>\n咖啡碱史蒂文非风机暗示", -1);
        oEpub.AddChapter("第2章", "今2天你当天嫩肤流口水司法会计地方<br>\n咖啡碱史蒂22222222文非风机暗示", -1);
        oEpub.SaveTo();
    }
*/    
}

