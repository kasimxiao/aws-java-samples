package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.dcv.DcvService;

/**
 * DCV Presign URL 生成
 */
public class DcvPresignTest {

    public static void main(String[] args) {
        String instanceId = "i-0bc286927a1779157";
        String serverIp = "3.71.179.124";

        DcvService dcvService = new DcvService(new AwsConfig());
        
        String url = dcvService.generatePresignedUrl(instanceId, serverIp, "console", "ubuntu");
        System.out.println("\nDCV 免密登录 URL:\n" + url);
    }
}
