package com.quickutil.platform;

import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.GroupGrantee;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.Permission;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import ch.qos.logback.classic.Logger;

public class AWSS3Util {
	
	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(AWSS3Util.class);

	private static Map<String, Map<String, String>> bucketMap = new HashMap<String, Map<String, String>>();

	/**
	 * 初始化
	 * 使用的 S3 配置的 Properties 应该包含
	 * $S3Name.access_key
	 * $S3Name.secret_key
	 * $S3Name.endpoint
	 * $S3Name.region
	 * $S3Name.bucket
	 * 
	 * @param properties-S3配置
	 * @return
	 */
	public static boolean init(Properties properties) {
		Enumeration<?> keys = properties.propertyNames();
		Set<String> keyList = new HashSet<String>();
		while (keys.hasMoreElements()) {
			String key = (String) keys.nextElement();
			key = key.split("\\.")[0];
			keyList.add(key);
		}
		for (String key : keyList) {
			try {
				Map<String, String> map = new HashMap<String, String>();
				map.put("access_key", properties.getProperty(key + ".access_key"));
				map.put("secret_key", properties.getProperty(key + ".secret_key"));
				map.put("endpoint", properties.getProperty(key + ".endpoint"));
				map.put("region", properties.getProperty(key + ".region"));
				map.put("bucket", properties.getProperty(key + ".bucket"));
				bucketMap.put(key, map);
			} catch (Exception e) {
				LOGGER.error("",e);
			}
		}
		return true;
	}

	/**
	 * 获取客户端实例
	 * 
	 * @param s3Name-S3Name
	 * @return
	 */
	public static AmazonS3 buildClient(String s3Name) {
		AmazonS3ClientBuilder s3Builder = AmazonS3ClientBuilder.standard();
		s3Builder.setCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(bucketMap.get(s3Name).get("access_key"), bucketMap.get(s3Name).get("secret_key"))));
		s3Builder.setEndpointConfiguration(new EndpointConfiguration(bucketMap.get(s3Name).get("endpoint"), bucketMap.get(s3Name).get("region")));
		return s3Builder.build();
	}

	/**
	 * 获取文件列表
	 * 
	 * @param s3Name-S3Name
	 * @param prefix-文件前缀
	 * @return
	 */
	public static List<String> list(String s3Name, String prefix) {
		List<String> filePaths = new ArrayList<String>();
		final ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketMap.get(s3Name).get("bucket")).withMaxKeys(100);
		req.setPrefix(prefix);
		ListObjectsV2Result result;
		do {
			result = buildClient(s3Name).listObjectsV2(req);
			for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
				filePaths.add(objectSummary.getKey());
			}
			req.setContinuationToken(result.getNextContinuationToken());
		} while (result.isTruncated());
		return filePaths;
	}

	/**
	 * 上传
	 * 
	 * @param s3Name-S3Name
	 * @param bt-文件内容
	 * @param filePath-文件路径
	 * @param contentType-文件类型
	 */
	public static String uploadFile(String s3Name, byte[] bt, String filePath, String contentType, GroupGrantee groupGrantee, Permission permission) {
		InputStream is = new ByteArrayInputStream(bt);
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(bt.length);
		if (contentType != null)
			metadata.setContentType(contentType);
		AccessControlList acl = new AccessControlList();
		acl.grantPermission(groupGrantee, permission);
		buildClient(s3Name).putObject(new PutObjectRequest(bucketMap.get(s3Name).get("bucket"), filePath, is, metadata).withAccessControlList(acl));
		return bucketMap.get(s3Name).get("endpoint") + "/" + bucketMap.get(s3Name).get("bucket") + "/" + filePath;
	}

	/**
	 * 下载
	 * 
	 * @param s3Name-S3Name
	 * @param filePath-文件路径
	 * @return
	 */
	public static byte[] downloadFile(String s3Name, String filePath) {
		S3Object object = buildClient(s3Name).getObject(bucketMap.get(s3Name).get("bucket"), filePath);
		return FileUtil.stream2byte(object.getObjectContent());
	}

	/**
	 * 生成临时链接
	 * 
	 * @param s3Name-S3Name
	 * @param filePath-文件路径
	 * @param expireSeconds-链接有效时间，秒
	 * @return
	 * 
	 */
	public static URL generatePresignedUrl(String s3Name, String filePath, int expireSeconds) {
		return buildClient(s3Name).generatePresignedUrl(bucketMap.get(s3Name).get("bucket"), filePath, new Date(System.currentTimeMillis() + expireSeconds * 1000));
	}

	/**
	 * 删除
	 * 
	 * @param s3Name-S3Name
	 * @param filePath-文件路径
	 */
	public static void deleteFile(String s3Name, String filePath) {
		buildClient(s3Name).deleteObject(bucketMap.get(s3Name).get("bucket"), filePath);
	}
}
