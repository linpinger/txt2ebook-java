package com.ray.tools.umd.builder;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <P>I create this class base on some article from WWW. You can refer to:
 * http://www.javaeye.com/topic/465443</P>
 * <P>This is the main UMD class. It includes all step of making UMD file.
 * The use of this class is simple. such as follow:</P>
 * <pre>
 * public void generateUmd() throws IOException {
 *     Umd umd = new Umd();
 *     
 *     UmdHeader uh = umd.getHeader();
 *     uh.setTitle("Title of umd book");
 *     uh.setAuthor("Ray Liang");
 *     uh.setBookType("Super book");
 *     uh.setYear("2009");
 *     uh.setMonth("12");
 *     uh.setDay("25");
 *     uh.setBookMan("The book man");
 *     uh.setShopKeeper("The shop keeper");
 *     
 *     UmdChapters uc = umd.getChapters();
 *     File p = new File("./");
 *     uc.addFile(new File(p, "file_01.txt"));
 *     uc.addFile(new File(p, "file_02.txt"));
 *     uc.addFile(new File(p, "file_03.txt"));
 *     
 *     // umd.getCover().load("./cover.jpg");
 *     // umd.getCover().initDefaultCover("Title of UMD book");
 *     
 *     FileOutputStream fos = new FileOutputStream("./first_demo.umd");
 *     try {
 *         BufferedOutputStream bos = new BufferedOutputStream(fos);
 *         umd.buildUmd(bos);
 *         bos.flush();
 *     } finally {
 *         fos.close();
 *     }
 * }
 * </pre>
 * 
 * @author Ray Liang (liangguanhui@qq.com)
 * 2009-12-20
 */
public class Umd {
	
	/** Header Part of UMD book */
	private UmdHeader header = new UmdHeader();
	
	/**
	 * Detail chapters Part of UMD book
	 * (include Titles & Contents of each chapter)
	 */
	private UmdChapters chapters = new UmdChapters();
	
	/** Cover Part of UMD book (for example, and JPEG file) */
	private UmdCover cover = new UmdCover();
	
	/** End Part of UMD book */
	private UmdEnd end = new UmdEnd();
	
	/**
	 * Build the UMD file.
	 * @param os
	 * @throws IOException
	 */
	public void buildUmd(OutputStream os) throws IOException {
		WrapOutputStream wos = new WrapOutputStream(os);
		
		header.buildHeader(wos);
		chapters.buildChapters(wos);
		cover.buildCover(wos);
		end.buildEnd(wos);
	}

	public UmdHeader getHeader() {
		return header;
	}

	public void setHeader(UmdHeader header) {
		this.header = header;
	}

	public UmdChapters getChapters() {
		return chapters;
	}

	public void setChapters(UmdChapters chapters) {
		this.chapters = chapters;
	}

	public UmdCover getCover() {
		return cover;
	}

	public void setCover(UmdCover cover) {
		this.cover = cover;
	}

	public UmdEnd getEnd() {
		return end;
	}

	public void setEnd(UmdEnd end) {
		this.end = end;
	}

}
