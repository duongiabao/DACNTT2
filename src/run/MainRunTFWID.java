package run;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import algorithm.TFWID;

public class MainRunTFWID
{
    public static void main(String [] arg) throws IOException {
        
        String inputTrans = fileToPath("chess.tran");
        String inputWeights = fileToPath("chess.pro");
        String output = "outputTFWID.txt";
        
        TFWID algorithm = new TFWID();
        int rank = 50;
        algorithm.executeAlogrithm(inputTrans, inputWeights, output, rank);
        System.out.println("rank: "+rank);
        algorithm.printStats();
    }

    public static String fileToPath(String fileName) throws UnsupportedEncodingException {
        URL url = MainRunTFWINplus.class.getResource(fileName);
        return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }
}
