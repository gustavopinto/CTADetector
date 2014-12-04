package concurrencypatterns.util;

import org.eclipse.core.resources.IFile;

public class PrintableString {
    private String idiom;
    private String fileName;
    private String lineNum;
    private String idiomType;
    private IFile file;
    
    private boolean hasSynchronized;

    public PrintableString(String i, String name, String line, IFile f, boolean sync) {
        idiom = i;
        fileName = name;
        lineNum = line;
        file = f;
        hasSynchronized = sync;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PrintableString) {
            PrintableString ps2 = (PrintableString)o;
            String s1 = fileName + lineNum;
            String s2 = ps2.fileName + ps2.lineNum;
            return s1.equals(s2);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (fileName + lineNum).hashCode();
    }
    
    public String toString(){
        String content = idiom + " " + fileName + ": " + lineNum;
        return hasSynchronized? "(Extra sync) " + content : content;
    }
    
    public boolean isSync(){
        return hasSynchronized;
    }
    
    public void setIdiomType(String type) {
        idiomType = type;
    }
    
    public String getIdiom() {
        return idiom;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public String getLineNum() {
        return lineNum;
    }
    
    public IFile getFile() {
        return file;
    }
    
    public String getType() {
        return idiomType;
    }
}
