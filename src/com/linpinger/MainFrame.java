
package com.linpinger;

import com.linpinger.tool.FileDrop;
import com.linpinger.tool.FoxEpubWriter;
import com.linpinger.tool.ToolJava;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Vector;
import javax.swing.JFileChooser;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import com.ray.tools.umd.builder.Umd;
import com.ray.tools.umd.builder.UmdChapters;
import com.ray.tools.umd.builder.UmdHeader;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

public class MainFrame extends javax.swing.JFrame {
    String defOutDir = "/dev/shm/" ; // 默认输出目录，如果不存在，就是txt所在目录
    final String myBodyFontStyle = "\t\t@font-face { font-family: \"hei\"; src: local(\"Zfull-GB\"); }\n\t\t.content { font-family: \"hei\"; }\n";
    String BodyFontStyle = myBodyFontStyle;
    boolean DelHTML = true ; // 批量txt2mobi时删除临时html文件

    final int OUT_MOBI = 1;
    final int OUT_EPUB = 2;
    final int OUT_UMD = 3;
    final int OUT_TXT = 9;

    private HashMap<String,Object> setting ;
    private javax.swing.table.DefaultTableModel tList;

    public MainFrame() {
        initComponents();
        this.setLocationRelativeTo(null); // 屏幕居中显示

        TableColumnModel tcmL = uChapters.getTableHeader().getColumnModel();
        tcmL.getColumn(0).setPreferredWidth(500);
        tcmL.getColumn(1).setPreferredWidth(80);

        tList = (DefaultTableModel)uChapters.getModel();
        
        dlgSetting.setSize(dlgSetting.getPreferredSize());
        dlgSetting.setLocationRelativeTo(null);
        // ESC 退出子窗口
        dlgSetting.getRootPane().registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dlgSetting.dispose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        
        setting = new HashMap<String,Object>(9);
        setting.put("bookname", "狐狸之书");
        setting.put("author", "爱尔兰之狐");

        // 拖动:  http://iharder.sourceforge.net/current/java/filedrop/
        new FileDrop(this, new FileDrop.Listener() {  // 拖动到窗口上就可以处理
            public void filesDropped(java.io.File[] files) {
                int fileCount = files.length ;
                if (fileCount == 0) {
                    return;
                }
                String txtPath = "" ;
                if (fileCount == 1) { // 单个文件
                    txtPath = files[0].getPath();
                    System.out.println("- Drag & Drop: " + txtPath);
                    cbTxtPath.setText(txtPath);

                    readAndTestTxt(txtPath); // 测试文本编码
                } else { // 多个文件批量处理
                    for (int i = 0; i < fileCount; i++) {
                        txtPath = files[i].getPath();
                        System.out.println("- Processing: " + txtPath);
                        cbTxtPath.setText(txtPath);
                        msg.setText((1 + i) + " / " + fileCount + " : " + txtPath);

                        createMobi(files[i]); // txt -> mobi
                    }
                    msg.setText("批量转txt为mobi完毕，共 " + fileCount +  " 个文件");
                }

            }   // end filesDropped
        }); // end FileDrop.Listener

    }

    public void readAndTestTxt(String txtPath) {
        cbTxtPath.setText(txtPath);
        tList.setRowCount(0); // 清空列表
        String nowTxtEncoding = "GBK";
        if (mTxtAuto.isSelected()) {
            nowTxtEncoding = ToolJava.detectTxtEncoding(txtPath);
            msg.setText("★　自动检测到的编码: " + nowTxtEncoding + "　　" + txtPath);
        }
        if (mTxtUTF8NoBOM.isSelected()) {
            nowTxtEncoding = "UTF-8";
            msg.setText("★　人工指定的编码: UTF-8　　" + txtPath);
        }
        if (mTxtGBK.isSelected()) {
            nowTxtEncoding = "GBK";
            msg.setText("★　人工指定的编码: GBK　　" + txtPath);
        }
        setting.put("txtencoding", nowTxtEncoding);

        // 书名
        File xxx = new File(txtPath);
        String namewithext = xxx.getName();
        if (namewithext.toLowerCase().endsWith(".txt")) {
            setting.put("bookname", namewithext.substring(0, namewithext.length() - 4));
        } else {
            setting.put("bookname", namewithext);
        }
    }

    void preProcessTxt() {
        File txt = new File(cbTxtPath.getText());
        if (!txt.exists()) { // 如果txt文件不存在
            msg.setText("★　错误: 文本路径不正确，你还木有拖文件进来咩");
            return;
        }

        String nowRE = cbTitleRE.getSelectedItem().toString();

        // 标题长度
        int titleMax = 4096;
        if (ckTitleLen.isSelected()) {
            titleMax = Integer.valueOf(edtTitleLen.getText());
        }

        tList.setRowCount(0); // 清空记录
        long sTime = System.currentTimeMillis();

        try { // 一行行匹配RE
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(txt), (String) setting.get("txtencoding")));
            String line = "";
            int nLine = 0;
            while ((line = br.readLine()) != null) {
                ++nLine;
                if (line.length() < titleMax) {
                    if (line.matches(nowRE)) {
                        tList.addRow(new Object[]{line, String.valueOf(nLine)});
                    }
                }
            }
            br.close();
        } catch (Exception e) {
            System.err.println(e.toString());
        }

        msg.setText("★　目录定位完毕，耗时(ms): " + (System.currentTimeMillis() - sTime));
    }

    void createMobi(File txt) {
        // 获取保存路径
        File saveDir = new File(defOutDir);
        if (!saveDir.exists()) {
            saveDir = txt.getParentFile(); // txt所在路径
        }

        String bookname = txt.getName().replace(".txt", "");
        File saveFile = new File(saveDir, bookname + ".html");

        String html = "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"zh-CN\">\n<head>\n\t<title>"
                + bookname + "</title>\n\t<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n\t<style type=\"text/css\">\n\t\th2,h3,h4{text-align:center;}\n"
                + BodyFontStyle + "\t</style>\n</head>\n<body>\n<h2>"
                + bookname + "</h2>\n\n<div class=\"content\">\n\n"
                + ToolJava.readText( txt.getPath(), ToolJava.detectTxtEncoding( txt.getPath() ) ).replace("\r", "").replace("\n", "<br />\n")
                + "\n</div>\n</body>\n</html>\n";
        ToolJava.writeText(html, saveFile.getPath(), "UTF-8");
        
        // html -> mobi
        try {
            Process cmd = Runtime.getRuntime().exec("kindlegen " + saveFile.getName(), null, saveFile.getParentFile());
            // 缓冲区需要释放, 不然会阻塞 kindlegen
            InputStream iput = cmd.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iput));
            while (br.readLine() != null) ;
            br.close();
            iput.close();
            // 缓冲区需要释放
            cmd.waitFor();
        } catch (Exception e) {
            System.err.println(e.toString());
        }

        if (DelHTML) {
            saveFile.delete(); // 删除html文件
        }
    }

    void createEBook() {

        String tarFormat = cbEbook.getSelectedItem().toString(); // 要转换的格式: mobi, epub, txt
        int outFormat = 0;
        if ( tarFormat.equalsIgnoreCase("mobi")) { outFormat = OUT_MOBI; }
        if ( tarFormat.equalsIgnoreCase("epub")) { outFormat = OUT_EPUB; }
        if ( tarFormat.equalsIgnoreCase("umd")) { outFormat = OUT_UMD; }
        if ( tarFormat.equalsIgnoreCase("txt")) { outFormat = OUT_TXT; }

        String txtPath = cbTxtPath.getText();
        File txt = new File(txtPath);
        if ( ! txt.exists() ) { // 如果txt文件不存在
            msg.setText("★　错误: 文本路径不正确，你还木有拖文件进来咩");
            return;
        }

        // 读取文本，并切割成字符串数组
        String ttt = ToolJava.readText(txtPath, (String)setting.get("txtencoding"));  // 444K 耗时 4s
        String l[] = ttt.split("[\r]?\n");
        ttt = ""; //
        int lCount = l.length;  // 文本行数
        
        // 获取保存路径
        String outDir =  defOutDir;
        File saveDir = new File(outDir) ;
        if ( !saveDir.exists() ) {
            outDir = new File(txtPath).getParent() + File.separator; // txt所在路径
        }

        // 获取TOC: 标题:行号
        Vector lists = tList.getDataVector();
        int listslen = lists.size();
        if ( listslen == 0 ) {
            msg.setText("★　错误: 目录项为0，你还木有定位目录吧，按一下按钮定位");
            return ;
        }

        // 获取处理过程中选项
        boolean bDelBlankLine = mDelBlankLine.isSelected();
        boolean bDelHeadSpace = mDelHeadSpace.isSelected();
    
        String bookname = (String)setting.get("bookname") ; // 书名
        String author = (String)setting.get("author") ;

        msg.setText("★　开始生成: " + bookname);
        long sTime = System.currentTimeMillis();

        // 格式初始化工作

        FoxEpubWriter oEpub = null ;
        BufferedWriter bw = null ;
        Umd umd = null;
        UmdChapters  cha = null ;
        switch (outFormat) {
            case OUT_MOBI:
                oEpub = new FoxEpubWriter(new File(outDir + bookname + ".mobi"), bookname);
                oEpub.BookCreator = author;
                oEpub.BodyFontStyle = BodyFontStyle;
                break;
            case OUT_EPUB:
                oEpub = new FoxEpubWriter(new File(outDir + bookname + ".epub"), bookname);
                oEpub.BookCreator = author;
                oEpub.BodyFontStyle = BodyFontStyle;
                break;
            case OUT_UMD:
                umd = new Umd();
                UmdHeader uh = umd.getHeader(); // 设置书籍信息
                uh.setTitle(bookname);
                uh.setAuthor(author);
                uh.setYear("2014");
                uh.setMonth("09");
                uh.setDay("01");
                uh.setBookMan("爱尔兰之狐");
                uh.setShopKeeper("爱尔兰之狐");
                cha = umd.getChapters(); // 设置内容
                break;
            case OUT_TXT:
                try {
                    bw = new BufferedWriter(new FileWriter(outDir + bookname + "_fox.txt", false));
                } catch (Exception e) {
                    System.err.println(e.toString());
                }
                break;
        }

        int chaTitleNum = 0;
        int nextChaTitleNum = 0 ;
        String chaTitle = ""; // 章节标题
        StringBuffer chaContent ; // 章节内容
        String line ="";
        boolean isCleanTitleHead = mDelTitleHeadSpace.isSelected();
        for ( int i=0; i < listslen; i++) {
            chaTitle = (String)((Vector)lists.get(i)).get(0);  // 标题行
            chaTitleNum = Integer.valueOf((String)((Vector)lists.get(i)).get(1)); // 标题行号

            if ( isCleanTitleHead ) {
                chaTitle = chaTitle.replaceAll("^[ \t　]*", ""); // 删除头部空白
            }
            if (  ( 1 + i ) == listslen ) { // 最后一个记录
                nextChaTitleNum = lCount + 1; // 下一个标题行号
            } else { // 
                nextChaTitleNum = Integer.valueOf((String)((Vector)lists.get(i+1)).get(1)); // 下一个标题行号
            }

            chaContent = new StringBuffer(40960);
            for (int j = chaTitleNum + 1; j < nextChaTitleNum; j++) { // 两个标题行之间的区间即为正文内容
                line = l[j-1];
                if (bDelHeadSpace) {
                    line = line.replaceAll("^[ \t　]*", "　　"); // 替换头部空白为两个空格
                }
                if ( bDelBlankLine && ( line.isEmpty() || line.equalsIgnoreCase("　　") )) {
                    continue;
                }
                chaContent.append(line).append("<br />\n"); // 行
            }

            switch (outFormat) {
                case OUT_MOBI:
                    oEpub.addChapter(chaTitle, chaContent.toString());
                    break;      
                case OUT_EPUB:
                    oEpub.addChapter(chaTitle, chaContent.toString());
                    break;
                case OUT_UMD:
                    cha.addChapter(chaTitle, chaContent.toString());
                    break;
                case OUT_TXT:
                    try {
                        bw.write(chaTitle + "\n\n" + chaContent.toString());
                    } catch (Exception e) {
                        e.toString();
                    }
                    break;
            }
        }

        switch (outFormat) { // 结尾工作
            case OUT_MOBI:
                oEpub.saveAll();
                break;
            case OUT_EPUB:
                oEpub.saveAll();
                break;
            case OUT_UMD:
                File file = new File(outDir + bookname + ".umd"); // 生成
                try {
                    FileOutputStream fos = new FileOutputStream(file);
                    try {
                        BufferedOutputStream bos = new BufferedOutputStream(fos);
                        umd.buildUmd(bos);
                        bos.flush();
                    } finally {
                        fos.close();
                    }
                } catch (Exception e) {
                    e.toString();
                }
                break;
            case OUT_TXT:
                try {
                    bw.flush();
                    bw.close();
                } catch (Exception e) {
                    e.toString();
                }            
                break;
        }
        msg.setText("★　生成完毕，耗时(ms): " + (System.currentTimeMillis() - sTime));
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        chooseTxt = new javax.swing.JFileChooser();
        dlgSetting = new javax.swing.JDialog();
        jPanel5 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        edtBookName = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        edtBookAuthor = new javax.swing.JTextField();
        jButton2 = new javax.swing.JButton();
        bgTxtEncoding = new javax.swing.ButtonGroup();
        jPopupMenuItem = new javax.swing.JPopupMenu();
        mInsertChapter = new javax.swing.JMenuItem();
        mDeleteMultiChapters = new javax.swing.JMenuItem();
        jPanel1 = new javax.swing.JPanel();
        btnOpenTxt = new javax.swing.JButton();
        cbTxtPath = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        cbEbook = new javax.swing.JComboBox();
        jPanel2 = new javax.swing.JPanel();
        ckTitleRE = new javax.swing.JCheckBox();
        cbTitleRE = new javax.swing.JComboBox();
        ckTitleLen = new javax.swing.JCheckBox();
        edtTitleLen = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        uChapters = new javax.swing.JTable();
        jPanel4 = new javax.swing.JPanel();
        btnBuildEbook = new javax.swing.JButton();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        mWinOnTop = new javax.swing.JCheckBoxMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        mTxtAuto = new javax.swing.JRadioButtonMenuItem();
        mTxtUTF8NoBOM = new javax.swing.JRadioButtonMenuItem();
        mTxtGBK = new javax.swing.JRadioButtonMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        mDelTitleHeadSpace = new javax.swing.JCheckBoxMenuItem();
        mDelHeadSpace = new javax.swing.JCheckBoxMenuItem();
        mDelBlankLine = new javax.swing.JCheckBoxMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        jCheckBoxMenuItem1 = new javax.swing.JCheckBoxMenuItem();
        mDelTmpHTML = new javax.swing.JCheckBoxMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        mSettingDlg = new javax.swing.JMenuItem();
        msg = new javax.swing.JMenu();

        chooseTxt.setApproveButtonMnemonic(1);
        chooseTxt.setDialogTitle("选择要转换的文本文件");

        dlgSetting.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        dlgSetting.setTitle("设置啊");
        dlgSetting.setModal(true);

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "书籍信息:", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("文泉驿微米黑", 1, 14), new java.awt.Color(0, 0, 255))); // NOI18N
        jPanel5.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N

        jLabel2.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        jLabel2.setText("书名:");

        edtBookName.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        edtBookName.setText("狐狸之书");

        jLabel3.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        jLabel3.setText("作者:");

        edtBookAuthor.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        edtBookAuthor.setText("爱尔兰之狐");

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(edtBookName, javax.swing.GroupLayout.PREFERRED_SIZE, 173, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(edtBookAuthor, javax.swing.GroupLayout.PREFERRED_SIZE, 173, javax.swing.GroupLayout.PREFERRED_SIZE))))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(edtBookName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(edtBookAuthor, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        jButton2.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        jButton2.setMnemonic('s');
        jButton2.setText("保存(S)");
        jButton2.setToolTipText("");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout dlgSettingLayout = new javax.swing.GroupLayout(dlgSetting.getContentPane());
        dlgSetting.getContentPane().setLayout(dlgSettingLayout);
        dlgSettingLayout.setHorizontalGroup(
            dlgSettingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dlgSettingLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        dlgSettingLayout.setVerticalGroup(
            dlgSettingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dlgSettingLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(dlgSettingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 37, Short.MAX_VALUE))
        );

        mInsertChapter.setFont(new java.awt.Font("文泉驿微米黑", 0, 18)); // NOI18N
        mInsertChapter.setMnemonic('i');
        mInsertChapter.setText("插入新章节(I)");
        mInsertChapter.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mInsertChapterActionPerformed(evt);
            }
        });
        jPopupMenuItem.add(mInsertChapter);

        mDeleteMultiChapters.setFont(new java.awt.Font("文泉驿微米黑", 0, 18)); // NOI18N
        mDeleteMultiChapters.setMnemonic('d');
        mDeleteMultiChapters.setText("删除选中的章节(D)");
        mDeleteMultiChapters.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mDeleteMultiChaptersActionPerformed(evt);
            }
        });
        jPopupMenuItem.add(mDeleteMultiChapters);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Txt2Ebook");
        setAlwaysOnTop(true);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "第一步: 选择要转换的文本文件，全局设置", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("文泉驿微米黑", 1, 14), new java.awt.Color(0, 0, 255))); // NOI18N
        jPanel1.setFont(new java.awt.Font("SimSun", 1, 12)); // NOI18N

        btnOpenTxt.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        btnOpenTxt.setMnemonic('o');
        btnOpenTxt.setText("选择(O)");
        btnOpenTxt.setToolTipText("选择要转换的txt文件");
        btnOpenTxt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOpenTxtActionPerformed(evt);
            }
        });

        cbTxtPath.setEditable(false);
        cbTxtPath.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        cbTxtPath.setText("将txt文件拖动到这里");
        cbTxtPath.setToolTipText("这里表示txt完整路进，按左侧选择按钮，或将txt文件拖动到这里哦");

        jLabel1.setFont(new java.awt.Font("文泉驿微米黑", 1, 14)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(0, 0, 255));
        jLabel1.setText("->");

        cbEbook.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        cbEbook.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "mobi", "epub", "umd", "txt" }));
        cbEbook.setToolTipText("要转换成的格式");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(btnOpenTxt)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cbTxtPath)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cbEbook, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(cbEbook, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(jLabel1)
                .addComponent(btnOpenTxt, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(cbTxtPath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "第二步: 正则表达式定位标题", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("文泉驿微米黑", 1, 14), new java.awt.Color(0, 0, 255))); // NOI18N
        jPanel2.setFont(new java.awt.Font("SimSun", 1, 12)); // NOI18N

        ckTitleRE.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        ckTitleRE.setForeground(new java.awt.Color(0, 155, 0));
        ckTitleRE.setSelected(true);
        ckTitleRE.setText("标题正则:");
        ckTitleRE.setEnabled(false);

        cbTitleRE.setEditable(true);
        cbTitleRE.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        cbTitleRE.setModel(new javax.swing.DefaultComboBoxModel(new String[] { ".*[第]?[0-9零○一二两三四五六七八九十百千廿卅卌壹贰叁肆伍陆柒捌玖拾佰仟万１２３４５６７８９０]{1,5}[章节節堂讲回集部分品]?.*", ".*第[0-9零○一二两三四五六七八九十百千廿卅卌壹贰叁肆伍陆柒捌玖拾佰仟万１２３４５６７８９０]{1,5}[章节節堂讲回集部分品]{1}.*", ".*第.{1,5}章.*", ".*[0-9]{1,5}\\..*", "^#.*" }));
        cbTitleRE.setSelectedIndex(2);
        cbTitleRE.setToolTipText("搜索正则表达式学习一下吧，投入小，收益大哦");

        ckTitleLen.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        ckTitleLen.setForeground(new java.awt.Color(0, 155, 0));
        ckTitleLen.setSelected(true);
        ckTitleLen.setText("最长字数:");

        edtTitleLen.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        edtTitleLen.setText("25");
        edtTitleLen.setToolTipText("标题行包含的最大字数");

        jButton1.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        jButton1.setMnemonic('r');
        jButton1.setText("定位(R)");
        jButton1.setToolTipText("根据正则表达式定位标题");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(ckTitleRE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cbTitleRE, 0, 1, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ckTitleLen)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(edtTitleLen, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton1))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(ckTitleRE)
                .addComponent(cbTitleRE, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(ckTitleLen)
                .addComponent(edtTitleLen, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "第三步: 列表可编辑并影响目录结构，可右键菜单或Ctrl+Del", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("文泉驿微米黑", 1, 14), new java.awt.Color(0, 0, 255))); // NOI18N

        uChapters.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        uChapters.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "标题", "行号"
            }
        ));
        uChapters.setToolTipText("双击可修改单元格，右键弹出菜单");
        uChapters.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        uChapters.setRowHeight(22);
        uChapters.getTableHeader().setReorderingAllowed(false);
        uChapters.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                uChaptersMouseClicked(evt);
            }
        });
        uChapters.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                uChaptersKeyPressed(evt);
            }
        });
        jScrollPane1.setViewportView(uChapters);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 728, Short.MAX_VALUE)
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 265, Short.MAX_VALUE)
        );

        btnBuildEbook.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        btnBuildEbook.setMnemonic('s');
        btnBuildEbook.setText("生成(S)");
        btnBuildEbook.setToolTipText("啊，我是最后一步，按我生成电子书");
        btnBuildEbook.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBuildEbookActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(btnBuildEbook, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(btnBuildEbook, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jMenu1.setMnemonic('c');
        jMenu1.setText("设置(C)");
        jMenu1.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N

        mWinOnTop.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.ALT_MASK));
        mWinOnTop.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        mWinOnTop.setMnemonic('t');
        mWinOnTop.setSelected(true);
        mWinOnTop.setText("窗口置顶(T)");
        mWinOnTop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mWinOnTopActionPerformed(evt);
            }
        });
        jMenu1.add(mWinOnTop);
        jMenu1.add(jSeparator3);

        mTxtAuto.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        bgTxtEncoding.add(mTxtAuto);
        mTxtAuto.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        mTxtAuto.setSelected(true);
        mTxtAuto.setText("文本编码: 自动检测");
        mTxtAuto.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mTxtAutoActionPerformed(evt);
            }
        });
        jMenu1.add(mTxtAuto);

        mTxtUTF8NoBOM.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_U, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        bgTxtEncoding.add(mTxtUTF8NoBOM);
        mTxtUTF8NoBOM.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        mTxtUTF8NoBOM.setText("文本编码: UTF-8");
        mTxtUTF8NoBOM.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mTxtUTF8NoBOMActionPerformed(evt);
            }
        });
        jMenu1.add(mTxtUTF8NoBOM);

        mTxtGBK.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        bgTxtEncoding.add(mTxtGBK);
        mTxtGBK.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        mTxtGBK.setText("文本编码: GBK");
        mTxtGBK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mTxtGBKActionPerformed(evt);
            }
        });
        jMenu1.add(mTxtGBK);
        jMenu1.add(jSeparator1);

        mDelTitleHeadSpace.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        mDelTitleHeadSpace.setMnemonic('i');
        mDelTitleHeadSpace.setSelected(true);
        mDelTitleHeadSpace.setText("删除标题头部空白(I)");
        mDelTitleHeadSpace.setToolTipText("");
        jMenu1.add(mDelTitleHeadSpace);

        mDelHeadSpace.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        mDelHeadSpace.setSelected(true);
        mDelHeadSpace.setText("替换行首空白为两个中文空格");
        jMenu1.add(mDelHeadSpace);

        mDelBlankLine.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        mDelBlankLine.setSelected(true);
        mDelBlankLine.setText("删除空白行");
        jMenu1.add(mDelBlankLine);
        jMenu1.add(jSeparator2);

        jCheckBoxMenuItem1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.ALT_MASK));
        jCheckBoxMenuItem1.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        jCheckBoxMenuItem1.setMnemonic('f');
        jCheckBoxMenuItem1.setSelected(true);
        jCheckBoxMenuItem1.setText("使用字体Zfull-GB");
        jCheckBoxMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxMenuItem1ActionPerformed(evt);
            }
        });
        jMenu1.add(jCheckBoxMenuItem1);

        mDelTmpHTML.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        mDelTmpHTML.setMnemonic('d');
        mDelTmpHTML.setSelected(true);
        mDelTmpHTML.setText("批量自动转换时删除html文件(D)");
        mDelTmpHTML.setToolTipText("");
        mDelTmpHTML.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mDelTmpHTMLActionPerformed(evt);
            }
        });
        jMenu1.add(mDelTmpHTML);
        jMenu1.add(jSeparator4);

        mSettingDlg.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F12, java.awt.event.InputEvent.CTRL_MASK));
        mSettingDlg.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        mSettingDlg.setMnemonic('c');
        mSettingDlg.setText("其他设置(C)");
        mSettingDlg.setToolTipText("");
        mSettingDlg.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mSettingDlgActionPerformed(evt);
            }
        });
        jMenu1.add(mSettingDlg);

        jMenuBar1.add(jMenu1);

        msg.setForeground(new java.awt.Color(0, 155, 0));
        msg.setText("★　Txt2Ebook Java 版 by 爱尔兰之狐 Ver:2018-1-3");
        msg.setEnabled(false);
        msg.setFocusable(false);
        msg.setFont(new java.awt.Font("文泉驿微米黑", 0, 14)); // NOI18N
        jMenuBar1.add(msg);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // 按钮选择txt文件
    private void btnOpenTxtActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOpenTxtActionPerformed
        if (chooseTxt.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            readAndTestTxt(chooseTxt.getSelectedFile().getAbsolutePath());
        }
    }//GEN-LAST:event_btnOpenTxtActionPerformed

    // 生成电子书
    private void btnBuildEbookActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBuildEbookActionPerformed
        createEBook();
    }//GEN-LAST:event_btnBuildEbookActionPerformed

    // 预处理目录
    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        preProcessTxt();
    }//GEN-LAST:event_jButton1ActionPerformed

    private void mSettingDlgActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mSettingDlgActionPerformed
 // foxtodo
        dlgSetting.setSize(dlgSetting.getPreferredSize());
        dlgSetting.setLocationRelativeTo(null);
        
        edtBookName.setText((String)setting.get("bookname"));
        dlgSetting.setVisible(true);
 
    }//GEN-LAST:event_mSettingDlgActionPerformed

    private void mTxtAutoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mTxtAutoActionPerformed
        String nowTxtPath = cbTxtPath.getText();
        if ( ! nowTxtPath.equalsIgnoreCase("将txt文件拖动到这里") ) {
            setting.put("txtencoding", ToolJava.detectTxtEncoding(nowTxtPath));
        }
        msg.setText("★　自动检测文本编码");
    }//GEN-LAST:event_mTxtAutoActionPerformed

    private void mTxtUTF8NoBOMActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mTxtUTF8NoBOMActionPerformed
        setting.put("txtencoding", "UTF-8");
        msg.setText("★　人工指定的编码: UTF-8");
    }//GEN-LAST:event_mTxtUTF8NoBOMActionPerformed

    private void mTxtGBKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mTxtGBKActionPerformed
        setting.put("txtencoding", "GBK");
        msg.setText("★　人工指定的编码: GBK");
    }//GEN-LAST:event_mTxtGBKActionPerformed

    private void uChaptersMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_uChaptersMouseClicked
        if (java.awt.event.MouseEvent.BUTTON3 == evt.getButton()) {
            int nSel = uChapters.getSelectedRowCount();
//            System.out.println("选中数量: " + nSel);
            if (nSel <= 1) { // 当选中1行时
                int nRow = uChapters.rowAtPoint(evt.getPoint());
                uChapters.setRowSelectionInterval(nRow, nRow);
            }
            jPopupMenuItem.show(evt.getComponent(), evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_uChaptersMouseClicked

    private void mInsertChapterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mInsertChapterActionPerformed
        int nRow = uChapters.getSelectedRow();
        // String nChaName = uChapters.getValueAt(nRow, 0).toString();
        int nChaNum = Integer.valueOf(uChapters.getValueAt(nRow, 1).toString());
        //System.out.println(nChaNum + "|" + nChaName);
        tList.insertRow(nRow, new Object[]{"★序", String.valueOf(nChaNum - 5)});
    }//GEN-LAST:event_mInsertChapterActionPerformed

    void deleteSelectedRows() {
        int[] nRow = uChapters.getSelectedRows(); // 选中的所有行号
        for (int i = nRow.length - 1; i >= 0; i--) { // 倒序删除 LV 中显示条目
            tList.removeRow(nRow[i]);
        }
    }

    private void mDeleteMultiChaptersActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mDeleteMultiChaptersActionPerformed
        deleteSelectedRows();
    }//GEN-LAST:event_mDeleteMultiChaptersActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        setting.put("bookname", edtBookName.getText());
        setting.put("author", edtBookAuthor.getText());
        dlgSetting.dispose();
    }//GEN-LAST:event_jButton2ActionPerformed

    private void mWinOnTopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mWinOnTopActionPerformed
        if ( this.isAlwaysOnTop() ) {
            this.setAlwaysOnTop(false);
            msg.setText("窗口未置顶");
        } else {
            this.setAlwaysOnTop(true);
            msg.setText("窗口已置顶");
        }
    }//GEN-LAST:event_mWinOnTopActionPerformed

    private void uChaptersKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_uChaptersKeyPressed
        if (evt.isControlDown() && evt.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE) {
            deleteSelectedRows();
        }
    }//GEN-LAST:event_uChaptersKeyPressed

    private void jCheckBoxMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxMenuItem1ActionPerformed
        if (jCheckBoxMenuItem1.isSelected()) {
            BodyFontStyle = myBodyFontStyle;
            msg.setText("使用字体");
            System.out.println("- Font: myFont");
        } else {
            BodyFontStyle = "";
            msg.setText("不使用字体");
            System.out.println("- Font: 空");
        }
    }//GEN-LAST:event_jCheckBoxMenuItem1ActionPerformed

    private void mDelTmpHTMLActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mDelTmpHTMLActionPerformed
        if ( DelHTML ) {
            msg.setText("设置: 批量转换txt为mobi时，不删除临时html文件");
        } else {
            msg.setText("设置: 批量转换txt为mobi时，删除临时html文件");
        }
        DelHTML = ! DelHTML ;
    }//GEN-LAST:event_mDelTmpHTMLActionPerformed

    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        javax.swing.UIManager.put("control", new Color(228, 242, 228));               // 控件背景色
        javax.swing.UIManager.put("nimbusLightBackground", new Color(228, 242, 228)); // 文本背景色
        javax.swing.UIManager.put("nimbusSelectionBackground", new Color(129, 193, 115));          // 选定文本 129, 193, 115    55, 165, 55    64, 128, 128 

        javax.swing.UIManager.put("nimbusBlueGrey", new Color(179, 219, 179));        //控件色
        javax.swing.UIManager.put("nimbusBase", new Color(70, 140, 60));              //滚动条，基础颜色

        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ex) {
            System.err.println(ex.toString());
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup bgTxtEncoding;
    private javax.swing.JButton btnBuildEbook;
    private javax.swing.JButton btnOpenTxt;
    private javax.swing.JComboBox cbEbook;
    private javax.swing.JComboBox cbTitleRE;
    private javax.swing.JTextField cbTxtPath;
    private javax.swing.JFileChooser chooseTxt;
    private javax.swing.JCheckBox ckTitleLen;
    private javax.swing.JCheckBox ckTitleRE;
    private javax.swing.JDialog dlgSetting;
    private javax.swing.JTextField edtBookAuthor;
    private javax.swing.JTextField edtBookName;
    private javax.swing.JTextField edtTitleLen;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItem1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPopupMenu jPopupMenuItem;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JCheckBoxMenuItem mDelBlankLine;
    private javax.swing.JCheckBoxMenuItem mDelHeadSpace;
    private javax.swing.JCheckBoxMenuItem mDelTitleHeadSpace;
    private javax.swing.JCheckBoxMenuItem mDelTmpHTML;
    private javax.swing.JMenuItem mDeleteMultiChapters;
    private javax.swing.JMenuItem mInsertChapter;
    private javax.swing.JMenuItem mSettingDlg;
    private javax.swing.JRadioButtonMenuItem mTxtAuto;
    private javax.swing.JRadioButtonMenuItem mTxtGBK;
    private javax.swing.JRadioButtonMenuItem mTxtUTF8NoBOM;
    private javax.swing.JCheckBoxMenuItem mWinOnTop;
    private javax.swing.JMenu msg;
    private javax.swing.JTable uChapters;
    // End of variables declaration//GEN-END:variables
}
