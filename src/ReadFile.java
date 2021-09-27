import java.io.FileInputStream;
import java.io.IOException;

public class ReadFile {
    private FileInputStream fileInputStream;
    private String filePath;

    public ReadFile(String path) {
        this.filePath = path;
    }

    public StringBuilder readFile() {
        StringBuilder readStr = null;
        try {
            fileInputStream = new FileInputStream(this.filePath);
            byte[] fileBytes = new byte[fileInputStream.available()];
            fileInputStream.read(fileBytes);
            readStr = new StringBuilder(new String(fileBytes));
        } catch (IOException e) {
            e.printStackTrace();
        }
        int fileLen = readStr.length(), i = 0, status = 0;
        char readC;
        while (i < fileLen) {
            switch (status) {
                case 0:
                    readC = readStr.charAt(i++);
                    if (readC == '/') {
                        status = 1;
                    } else if (readC == '"') {
                        status = 5;
                    }
                    break;
                case 1:
                    // read '/'
                    //  i:    ^
                    readC = readStr.charAt(i++);
                    if (readC == '/') {
                        status = 2;
                        readStr.replace(i - 2, i, "  ");
                    } else if (readC == '*') {
                        status = 3;
                        readStr.replace(i - 2, i, "  ");
                    } else {
                        status = 0;
                    }
                    break;
                case 2:
                    //   read '//'
                    //      i :  ^
                    readC = readStr.charAt(i++);
                    if (readC == '\n') {
                        status = 0;
                    } else {
                        readStr.setCharAt(i - 1, ' ');
                    }
                    break;
                case 3:
                    //  read '/*'
                    //      i : ^
                    readC = readStr.charAt(i++);
                    if (readC != '\n' && readC != '*') {
                        readStr.setCharAt(i - 1, ' ');
                    } else if (readC == '*') {
                        readStr.setCharAt(i - 1, ' ');
                        status = 4;
                    }
                    break;
                case 4:
                    // read '/*...*'
                    //   i :       ^
                    readC = readStr.charAt(i++);
                    if (readC == '/') {
                        readStr.setCharAt(i - 1, ' ');
                        status = 0;
                    } else if (readC == '*') {
                        readStr.setCharAt(i - 1, ' ');
                    } else {
                        if (readC != '\n') {
                            readStr.setCharAt(i - 1, ' ');
                        }
                        status = 3;
                    }
                    break;
                case 5:
                    readC = readStr.charAt(i++);
                    if (readC == '"') {
                        status = 0;
                    }
                    break;
            }
        }
        return readStr;
    }


}
