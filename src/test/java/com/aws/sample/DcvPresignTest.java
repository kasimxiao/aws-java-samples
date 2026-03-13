package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.dcv.DcvService;

/**
 * DCV Presign URL 生成
 */
public class DcvPresignTest {

    public static void main(String[] args) {
        String instanceId = "i-0e02f5f1c7e252caf";
        String serverIp = "63.176.131.75";

        DcvService dcvService = new DcvService(new AwsConfig());
        
        String url = dcvService.generatePresignedUrl(instanceId, serverIp, "console", "ubuntu");
        System.out.println("\nDCV 免密登录 URL:\n" + url);
    }
}
