package com.ray.tools.umd.builder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;


public class UmdUtils {
	
	public static byte[] unicodeBytes(String s) {
		if (s == null) {
			throw new NullPointerException();
		}
		
		int len = s.length();
		byte[] ret = new byte[len * 2];
		int a, b, c;
		for (int i = 0; i < len; i++) {
			c = s.charAt(i);
			a = c >> 8;
			b = c & 0xFF;
			if (a < 0) {
				a += 0xFF;
			}
			if (b < 0) {
				b += 0xFF;
			}
			ret[i * 2] = (byte) b;
			ret[i * 2 + 1] = (byte) a;
		}
		return ret;
	}
	
	public static void saveFile(File f, byte[] content) throws IOException {
		FileOutputStream fos = new FileOutputStream(f);
		try {
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			bos.write(content);
			bos.flush();
			bos.close();
		} finally {
			fos.close();
		}
	}
	
	public static byte[] readFile(File f) throws IOException {
		FileInputStream fis = new FileInputStream(f);
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			BufferedInputStream bis = new BufferedInputStream(fis);
			int ch;
			while ((ch = bis.read()) >= 0) {
				baos.write(ch);
			}
			baos.flush();
			return baos.toByteArray();
		} finally {
			fis.close();
		}
	}
	
	private static Random random = new Random();
	
	public static byte[] genRandomBytes(int len) {
		if (len <= 0) {
			throw new IllegalArgumentException("Length must > 0: " + len);
		}
		byte[] ret = new byte[len];
		for (int i = 0; i < ret.length; i++) {
			ret[i] = (byte) random.nextInt(256);
		}
		return ret;
	}

}
