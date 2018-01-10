package com.linpinger.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class FoxEpubWriter {

	private File ePubFile ; // 生成的epub文件
	private FoxZipWriter zw ; // epub 写为 zip文件
	private File TmpDir ; // mobi 临时目录

	private boolean isEpub = true;

	private String BookName = "狐狸之书";
	private String BookCreator = "爱尔兰之狐";
	private String CSS="h2,h3,h4 { text-align: center; }\n\n@font-face { font-family: \"hei\"; src: local(\"Zfull-GB\"); }\n.content { font-family: \"hei\"; }\n";

	private final String DefNameNoExt = "FoxMake"; //默认文件名
	private String BookUUID = UUID.randomUUID().toString();

	ArrayList<HashMap<String, Object>> Chapter = new ArrayList<HashMap<String, Object>>(200); //章节结构:1:ID 2:Title 3:Level
	int ChapterID = 100; //章节ID

	public FoxEpubWriter(File oEpubFile) {
		this(oEpubFile, "FoxEBook");
	}

	public FoxEpubWriter(File oEpubFile, String iBookName) {
		ePubFile = oEpubFile;
		BookName = iBookName;

		isEpub = ePubFile.getName().toLowerCase().endsWith(".epub") ;

		if ( ePubFile.exists() ) {
			ePubFile.renameTo(new File(ePubFile.getPath() + System.currentTimeMillis()));
		}
		if ( isEpub ) {
			zw = new FoxZipWriter(ePubFile);
		} else { // mobi
			TmpDir = new File(ePubFile.getParentFile(), "FoxEpub_" + System.currentTimeMillis() );
			if ( TmpDir.exists()) {
				System.out.println("错误:目录存在: " + TmpDir.getPath());
			} else {
				new File(TmpDir, "html").mkdirs();
				new File(TmpDir, "META-INF").mkdirs();
			}
		}
	}

	public void setBookName(String bookName) {
		this.BookName = bookName;
	}

	public void setBookCreator(String creatorName) {
		this.BookCreator = creatorName;
	}

	public void setCSS(String css) { // 覆盖CSS
		this.CSS = css;
	}

	public void addChapter(String Title, String Content) {
		addChapter(Title, Content, -1, 1);
	}

	public void addChapter(String Title, String Content, int iPageID) {
		addChapter(Title, Content, iPageID, 1);
	}

	public void addChapter(String Title, String Content, int iPageID, int iLevel) {
		if (iPageID < 0) {
			++this.ChapterID;
		} else {
			this.ChapterID = iPageID;
		}

		HashMap<String, Object> cc = new HashMap<String, Object>();
		cc.put("id", this.ChapterID);
		cc.put("name", Title);
		cc.put("level", iLevel);
		Chapter.add(cc);

		this._CreateChapterHTML(Title, Content, this.ChapterID); //写入文件
	}

	public void saveAll() {
		this._CreateIndexHTM();
		this._CreateNCX();
		this._CreateOPF();
		this._CreateMiscFiles();

		if (isEpub) {
			zw.close();
		} else { // 生成mobi
			try {
				Process cmd = Runtime.getRuntime().exec("kindlegen " + DefNameNoExt + ".opf", null, TmpDir);
				// 缓冲区需要释放, 不然会阻塞 kindlegen
				InputStream iput = cmd.getInputStream();
				BufferedReader br = new BufferedReader(new InputStreamReader(iput));
				while ( br.readLine() != null) ;
				br.close();
				iput.close();
				// 缓冲区需要释放
				cmd.waitFor();
			} catch (Exception e) {
				System.err.println(e.toString());
			}
			File tmpF = new File(TmpDir, DefNameNoExt + ".mobi");
			if (tmpF.exists() && tmpF.length() > 555) {
				tmpF.renameTo(ePubFile);
				ToolJava.deleteDir(TmpDir); // 移除临时目录
			}
		}
	}

	private void _CreateNCX() { //生成NCX文件
		StringBuffer NCXList = new StringBuffer(4096);
		int DisOrder = 1; //初始 顺序, 根据下面的playOrder数据

		HashMap<String, Object> mm;
		int nowID = 0;
		String nowTitle = "";
		int nowLevel = 0;
		int nextLevel = 0;

		int chapterCount = Chapter.size();
		int lastIDX = chapterCount - 1;
		for (int i = 0; i < chapterCount; i++) {
			mm = Chapter.get(i);
			nowID = (Integer) mm.get("id");
			nowTitle = (String) mm.get("name");
			nowLevel = (Integer) mm.get("level");

			++DisOrder;
			if (i == lastIDX) { // 最后一个
				nextLevel = 1;
			} else {
				nextLevel = (Integer) Chapter.get(1 + i).get("level");
			}

			if (nowLevel < nextLevel) {
				NCXList.append("\t<navPoint id=\"").append(nowID)
						.append("\" playOrder=\"").append(DisOrder)
						.append("\"><navLabel><text>").append(nowTitle)
						.append("</text></navLabel><content src=\"html/").append(nowID)
						.append(".html\" />\n");
			} else if (nowLevel == nextLevel) {
				NCXList.append("\t\t<navPoint id=\"").append(nowID)
						.append("\" playOrder=\"").append(DisOrder)
						.append("\"><navLabel><text>").append(nowTitle)
						.append("</text></navLabel><content src=\"html/").append(nowID)
						.append(".html\" /></navPoint>\n");
			} else if (nowLevel > nextLevel) {
				NCXList.append("\t\t<navPoint id=\"").append(nowID)
						.append("\" playOrder=\"").append(DisOrder)
						.append("\"><navLabel><text>").append(nowTitle)
						.append("</text></navLabel><content src=\"html/").append(nowID)
						.append(".html\" /></navPoint>\n\t</navPoint>\n");
			}

	}

		StringBuffer XML = new StringBuffer(4096);
		XML.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE ncx PUBLIC \"-//NISO//DTD ncx 2005-1//EN\" \"http://www.daisy.org/z3986/2005/ncx-2005-1.dtd\">\n<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\" xml:lang=\"zh-cn\">\n<head>\n\t<meta name=\"dtb:uid\" content=\"")
			.append(BookUUID).append("\"/>\n\t<meta name=\"dtb:depth\" content=\"1\"/>\n\t<meta name=\"dtb:totalPageCount\" content=\"0\"/>\n\t<meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n\t<meta name=\"dtb:generator\" content=\"")
			.append(BookCreator).append("\"/>\n</head>\n<docTitle><text>")
			.append(BookName).append("</text></docTitle>\n<docAuthor><text>")
			.append(BookCreator).append("</text></docAuthor>\n<navMap>\n\t<navPoint id=\"toc\" playOrder=\"1\"><navLabel><text>目录:")
			.append(BookName).append("</text></navLabel><content src=\"").append(DefNameNoExt).append(".htm\"/></navPoint>\n")
			.append(NCXList).append("\n</navMap></ncx>\n");

		_SaveFile(XML.toString(), DefNameNoExt + ".ncx");
	}

	private void _CreateOPF() { //生成OPF文件
		String AddXMetaData = "";
		StringBuffer NowHTMLMenifest = new StringBuffer(4096);
		StringBuffer NowHTMLSpine = new StringBuffer(4096);

		HashMap<String, Object> mm;
		int nowID = 0;
		Iterator<HashMap<String, Object>> itr = Chapter.iterator();
		while (itr.hasNext()) {
			mm = itr.next();
			nowID = (Integer) mm.get("id");
			NowHTMLMenifest.append("\t<item id=\"page").append(nowID).append("\" media-type=\"application/xhtml+xml\" href=\"html/").append(nowID).append(".html\" />\n");
			NowHTMLSpine.append("\t<itemref idref=\"page").append(nowID).append("\" />\n");
		}

		// 图片列表加载这里
		String NowImgMenifest = "";
		if ( ! isEpub ) {
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
		_SaveFile(XML.toString(), DefNameNoExt + ".opf");
	}

	private void _CreateMiscFiles() { //生成 epub 必须文件 mimetype, container.xml
		_SaveFile(CSS, DefNameNoExt + ".css"); // 生成 CSS 文件

		if (isEpub) {
			zw.putBinFile("application/epub+zip".getBytes(), "mimetype", true); // epub规范，第一个文件必须为stored
		} else {
			ToolJava.writeText("application/epub+zip", TmpDir.getPath() + File.separator + "mimetype");
		}

		StringBuffer XML = new StringBuffer(256);
		XML.append("<?xml version=\"1.0\"?>\n<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n\t<rootfiles>\n\t\t<rootfile full-path=\"")
				.append(this.DefNameNoExt).append(".opf")
				.append("\" media-type=\"application/oebps-package+xml\"/>\n\t</rootfiles>\n</container>\n");
		_SaveFile(XML.toString(), "META-INF/container.xml");
	}

	private void _CreateChapterHTML(String Title, String Content, int iPageID) { //生成章节页面
		StringBuffer HTML = new StringBuffer(20480);  // <div class="mbppagebreak"></div>
		HTML.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"zh-CN\">\n<head>\n\t<title>")
				.append(Title)
				.append("</title>\n\t<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"../")
				.append(DefNameNoExt)
				.append(".css\" />\n</head>\n<body>\n<h3>")
				.append(Title)
				.append("</h3>\n<div class=\"content\">\n\n\n")
				.append(Content)
				.append("\n\n\n</div>\n</body>\n</html>\n");
		_SaveFile(HTML.toString(), "html/" + iPageID + ".html");
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
				.append(BookName).append("</title>\n\t<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"").append(DefNameNoExt).append(".css\" />\n</head>\n<body>\n<h2>")
				.append(BookName).append("</h2>\n<div class=\"toc\">\n\n").append(NowTOC).append("\n\n</div>\n</body>\n</html>\n");
		_SaveFile(XML.toString(), DefNameNoExt + ".htm");
	}

	private void _SaveFile(String content, String saveRelatePath) {
		if (isEpub) { // epub
			zw.putTextFile(content, saveRelatePath);
		} else { // mobi
			ToolJava.writeText(content, new File(TmpDir, saveRelatePath).getPath());
		}
	}

	public static void main(String[] args) {
		FoxEpubWriter oEpub = new FoxEpubWriter(new File("/dev/shm/jgj.epub"), "金刚经");
		oEpub.setBookName("金刚般若波罗蜜经");
		oEpub.setBookCreator("鸠摩罗什 译");
		oEpub.setCSS("h2,h3,h4 { text-align: center; }\n");
		oEpub.addChapter("第1章", "如是我闻:<br>\n　　一时，佛在舍卫国祇树给孤独园，与大比丘众千二百五十人俱。<br>\n", -1);
		oEpub.addChapter("真言", "　　那谟婆伽跋帝　钵喇壤　波罗弭多曳　唵伊利底　伊室利　输卢驮　毗舍耶　毗舍耶　莎婆诃<br>\n", -1);
		oEpub.saveAll();
	}

}

