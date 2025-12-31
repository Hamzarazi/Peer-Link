package p2p.utils;

import java.util.Random;

public class utilsUpload {

    public static int generatePort() {
        int DYNAMIC_START_POINT = 49152;
        int DYNAMIC_END_POINT = 65535;

        Random rand = new Random();
        return (rand.nextInt(DYNAMIC_END_POINT - DYNAMIC_START_POINT) + DYNAMIC_START_POINT);
    }

}
