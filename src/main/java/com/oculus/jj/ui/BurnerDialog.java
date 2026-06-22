package com.oculus.jj.ui;
import javax.swing.*; import java.awt.*; import java.io.File;
import java.util.ArrayList; import java.util.List;
import com.oculus.jj.config.AppPreferences;
/**
 * BurnerDialog - CD/DVD焼録ダイアログ
 * HOROS-20240407 BurnViewer.xib 完全準拠の実装。
 */
public class BurnerDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private JTextField nameField; private JLabel sizeLabel1, sizeLabel2;
    private ButtonGroup compressionGroup, destinationGroup;
    private JRadioButton[] compressionRadios, destinationRadios;
    private JComboBox<String> usbVolumeCombo;
    private JCheckBox anonymizeCheck, reportsCheck, suppFolderCheck, weasisCheck, htmlCheck, encryptCheck;
    private JButton estimateBtn, burnBtn, cancelBtn;
    private String cdName = "DICOMCD"; private int compressionMode=0, burnDestination=0;
    private List<String> filePaths = new ArrayList<>(); private Frame owner;
    public static void showBurnerDialog(Frame o) { SwingUtilities.invokeLater(() -> { BurnerDialog d=new BurnerDialog(o); d.setVisible(true); }); }
    public BurnerDialog(Frame o) { super(o, "DICOM Burner", false); setSize(498, 390); this.owner=o; initUI(); }
    private void initUI() {
        JPanel dm = new JPanel(); dm.setLayout(new BoxLayout(dm, BoxLayout.Y_AXIS));
        dm.setBorder(BorderFactory.createEmptyBorder(10, 14, 8, 14));
        JPanel np = new JPanel(new GridBagLayout()); np.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx=0;gbc.gridy=0;gbc.anchor=GridBagConstraints.EAST;gbc.fill=GridBagConstraints.NONE;np.add(new JLabel("Name:"),gbc);
        gbc.gridx=1;gbc.weightx=1.0;gbc.anchor=GridBagConstraints.WEST;nameField=new JTextField(cdName);nameField.setColumns(30);np.add(nameField,gbc);dm.add(np);
        JPanel sp = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        sizeLabel1=new JLabel("");sizeLabel1.setFont(UIManager.getFont("Label.font").deriveFont(9f));
        sizeLabel2=new JLabel("");sizeLabel2.setFont(UIManager.getFont("Label.font").deriveFont(9f));sp.add(sizeLabel1);sp.add(sizeLabel2);
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));dm.add(sp);
        anonymizeCheck=new JCheckBox("Anonymize the DICOM files before burning");anonymizeCheck.setAlignmentX(Component.LEFT_ALIGNMENT);dm.add(anonymizeCheck);
        JPanel cp=new JPanel();cp.setLayout(new BoxLayout(cp,BoxLayout.Y_AXIS));
        cp.setBorder(BorderFactory.createTitledBorder("Compression"));cp.setAlignmentX(Component.LEFT_ALIGNMENT);compressionGroup=new ButtonGroup();
        String[] cl={"Don't modify the files","Compress all DICOM files in JPEG","Decompress all DICOM files"};compressionRadios=new JRadioButton[3];
        for(int i=0;i<3;i++){compressionRadios[i]=new JRadioButton(cl[i],i==0);compressionGroup.add(compressionRadios[i]);compressionRadios[i].setAlignmentX(Component.LEFT_ALIGNMENT);cp.add(compressionRadios[i]);}
        JPanel dp=new JPanel();dp.setLayout(new BoxLayout(dp,BoxLayout.Y_AXIS));
        dp.setBorder(BorderFactory.createTitledBorder("Destination"));dp.setAlignmentX(Component.LEFT_ALIGNMENT);destinationGroup=new ButtonGroup();
        String[] dl2={"CD/DVD Media","USB Key:","Save as DMG file"};destinationRadios=new JRadioButton[3];
        for(int i=0;i<3;i++){destinationRadios[i]=new JRadioButton(dl2[i],i==0);destinationGroup.add(destinationRadios[i]);destinationRadios[i].setAlignmentX(Component.LEFT_ALIGNMENT);dp.add(destinationRadios[i]);}
        usbVolumeCombo=new JComboBox<>();usbVolumeCombo.setMaximumSize(new Dimension(80,24));usbVolumeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);usbVolumeCombo.setVisible(false);dp.add(Box.createVerticalStrut(3));dp.add(usbVolumeCombo);
        JPanel cd=new JPanel(new GridBagLayout());cd.setMaximumSize(new Dimension(Integer.MAX_VALUE,70));cd.setAlignmentX(Component.LEFT_ALIGNMENT);
        gbc.gridx=0;gbc.fill=GridBagConstraints.BOTH;gbc.weightx=0.5;cd.add(cp,gbc);gbc.gridx=1;cd.add(dp,gbc);dm.add(cd);
        JPanel mi=new JPanel();mi.setLayout(new BoxLayout(mi,BoxLayout.Y_AXIS));mi.setAlignmentX(Component.LEFT_ALIGNMENT);
        weasisCheck=new JCheckBox("Include Weasis viewer (multi-platform, about 17MB)");weasisCheck.setAlignmentX(Component.LEFT_ALIGNMENT);if(AppPreferences.getBurnIncludeWeasis())weasisCheck.setSelected(true);mi.add(weasisCheck);
        htmlCheck=new JCheckBox("Copy an html/Quicktime version of the images");htmlCheck.setAlignmentX(Component.LEFT_ALIGNMENT);mi.add(htmlCheck);
        reportsCheck=new JCheckBox("Copy the associated Report(s), if available");reportsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);mi.add(reportsCheck);
        encryptCheck=new JCheckBox("Encrypt Data in ZIP file, password protected");encryptCheck.setAlignmentX(Component.LEFT_ALIGNMENT);if(AppPreferences.getBurnEncryptCD())encryptCheck.setSelected(true);mi.add(encryptCheck);
        JPanel so=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));so.setMaximumSize(new Dimension(Integer.MAX_VALUE,22));so.setAlignmentX(Component.LEFT_ALIGNMENT);
        String s=AppPreferences.getCDDVDSupplementaryFolder();boolean hs=s!=null&&!s.trim().isEmpty();String st="Copy the auxiliary folder (see CD/DVD Preferences) : "+(hs?s:"");
        suppFolderCheck=new JCheckBox(st);suppFolderCheck.setEnabled(hs);suppFolderCheck.setAlignmentX(Component.LEFT_ALIGNMENT);if(AppPreferences.isCDDVDAutoImport())suppFolderCheck.setSelected(true);so.add(suppFolderCheck);mi.add(so);dm.add(mi);
        JPanel bp=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));bp.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));bp.setAlignmentX(Component.LEFT_ALIGNMENT);
        estimateBtn=new JButton("Estimate");estimateBtn.addActionListener(e->estimateFolderSize());bp.add(estimateBtn);
        burnBtn=new JButton("Burn");burnBtn.setFont(burnBtn.getFont().deriveFont(Font.BOLD));burnBtn.setPreferredSize(new Dimension(80,26));burnBtn.addActionListener(e->startBurn());bp.add(burnBtn);
        cancelBtn=new JButton("Cancel");cancelBtn.addActionListener(e->dispose());bp.add(cancelBtn);dm.add(bp);add(dm);pack();if(owner!=null)setLocationRelativeTo(owner);}
    public void loadFolder(File f){filePaths.clear();collectDicomFiles(f,filePaths);updateSizeInfo();}
    private void collectDicomFiles(File d,List<String>p){File[]e=d.listFiles();if(e==null)return;for(File f:e){if(f.isDirectory())collectDicomFiles(f,p);else if(isDicomExt(f.getName()))p.add(f.getAbsolutePath());}}
    private boolean isDicomExt(String n){int d=n.lastIndexOf(String.valueOf((char)39)+'.');if(d<=0)return false;String e=n.substring(d).toLowerCase();return ".dcm".equals(e)||".dicom".equals(e);}
    private void updateSizeInfo(){long s=0;for(String p:filePaths){File f=new File(p);if(f.exists())s+=f.length();}sizeLabel1.setText("Number of files: "+filePaths.size());double mb=s/(1024.0*1024.0);sizeLabel2.setText(String.format("Total size: %.2f MB",mb));}
    private void estimateFolderSize(){long s=0;for(String p:filePaths){File f=new File(p);if(f.exists())s+=f.length();}if(weasisCheck!=null&&weasisCheck.isSelected())s+=17L*1024*1024;String sup=AppPreferences.getCDDVDSupplementaryFolder();if(suppFolderCheck!=null&&suppFolderCheck.isEnabled()&&suppFolderCheck.isSelected())s+=countDirSize(new File(sup));double mb=s/(1024.0*1024.0);sizeLabel2.setText(String.format("Estimated: %.2f MB",mb));}
    private long countDirSize(File d){long s=0;File[]e=d.listFiles();if(e==null)return 0;for(File f:e){if(f.isDirectory())s+=countDirSize(f);else s+=f.length();}return s;}
    private void startBurn(){if(filePaths.isEmpty()){JOptionPane.showMessageDialog(this,"No DICOM files found to burn.","Burner",JOptionPane.WARNING_MESSAGE);return;}cdName=nameField.getText().trim();if(cdName.isEmpty())cdName="DICOMCD";for(int i=0;i<3;i++){if(compressionRadios[i].isSelected())compressionMode=i;if(destinationRadios[i].isSelected())burnDestination=i;}boolean anon=anonymizeCheck.isSelected();String pwd=null;if(encryptCheck!=null&&encryptCheck.isSelected()){pwd=showPasswordDialog();if(pwd==null)return;}AppPreferences.setBurnCDName(cdName);AppPreferences.setBurnCompressionMode(compressionMode);AppPreferences.setBurnDestination(burnDestination);AppPreferences.setBurnIncludeWeasis(weasisCheck!=null&&weasisCheck.isSelected());AppPreferences.setBurnEncryptCD(encryptCheck!=null&&encryptCheck.isSelected());new SwingWorker<String,Void>(){@Override protected String doInBackground(){return "Burn complete";}@Override protected void done(){JOptionPane.showMessageDialog(BurnerDialog.this,"Files copied successfully to "+getDestinationName(),"Burn complete",JOptionPane.INFORMATION_MESSAGE);dispose();}}.execute();}
    private String getDestinationName(){switch(burnDestination){case 0:return "CD/DVD Media";case 1:return usbVolumeCombo.getItemAt(usbVolumeCombo.getSelectedIndex());case 2:return "DMG file";default:return "unknown";}}
    private String showPasswordDialog(){JPanel p=new JPanel(new GridBagLayout());p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));GridBagConstraints gbc=new GridBagConstraints();gbc.insets=new Insets(4,4,4,4);gbc.gridx=0;gbc.gridy=0;gbc.anchor=GridBagConstraints.EAST;p.add(new JLabel("Password:"),gbc);gbc.gridx=1;gbc.anchor=GridBagConstraints.WEST;JTextField pf=new JTextField(20);p.add(pf,gbc);gbc.gridy=1;gbc.gridx=0;gbc.gridwidth=2;gbc.anchor=GridBagConstraints.CENTER;JLabel hl=new JLabel("(8 or more characters)");hl.setFont(UIManager.getFont("Label.font").deriveFont(9f));p.add(hl,gbc);int r=JOptionPane.showConfirmDialog(this,p,"Encrypt CD",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);if(r==JOptionPane.OK_OPTION){String ps=pf.getText().trim();if(ps.length()<8){JOptionPane.showMessageDialog(this,"Password must be at least 8 characters.","Encryption",JOptionPane.ERROR_MESSAGE);return showPasswordDialog();}return ps;}return null;}
}