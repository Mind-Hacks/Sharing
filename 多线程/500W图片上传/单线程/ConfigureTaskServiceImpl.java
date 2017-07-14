package com.ziroom.hire.service.ami.local.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ziroom.hire.domain.hire.BzBatchImg;
import com.ziroom.hire.domain.hire.BzHireAttachment;
import com.ziroom.hire.mapper.hire.BzBatchImgMapper;
import com.ziroom.hire.mapper.hire.BzHireAttachmentMapper;
import com.ziroom.hire.mapper.hire.BzHireContractMapper;
import com.ziroom.hire.service.ami.local.UploadImgService;
import com.ziroom.hire.service.ami.local.ConfigureTaskService;
import com.ziroom.hire.util.StringUtil;
import com.ziroom.tech.storage.client.domain.FileInfoResponse;
import com.ziroom.tech.storage.client.service.StorageService;


/**
* @description :分配任务的service
* @author :ZWenBO
* @time :2016年10月25日 下午3:46:33
* @version :1.0
*/
@Service
public class ConfigureTaskServiceImpl implements ConfigureTaskService {
	
	private static final Logger logger = Logger.getLogger(ConfigureTaskServiceImpl.class);

	// 资产的图片上传对象
	@Autowired
	private BzBatchImgMapper bzBatchImgMapper;

	// 合同
	@Autowired
	private BzHireContractMapper bzHireContractMapper;
	
	//引入别的service，目的是注入事务
	@Autowired
	private UploadImgService uploadService;
	/**
	 * @description: 将所有的文件名称入库
	 * @throws Exception
	 * @return void 
	 * @author :ZWenBO
	 */
	@Override
	public void ReadContractCode() throws Exception {
		File file=new File("G:"+File.separator);
		File[] files = file.listFiles(new ContractFilter("BJ"));	//过滤是合同的文件
		logger.info("共计文件==="+files.length);
		int point=0;
		
		for (File file2 : files) {
			if(file2.isDirectory()){
				BzBatchImg batchImg=new BzBatchImg();

				String fileName=file2.getName();
				batchImg.setHireContractCode(fileName);				//设置合同Code
				logger.info("当前刷取合同Code========="+fileName);
				
				String strContractId=bzHireContractMapper.getHireContractIdByHireContractCode(fileName);
				if(StringUtil.isBlank(strContractId)){
					batchImg.setHireContractId(null);
				}else{
					Long contractId= Long.valueOf(strContractId).longValue();
					batchImg.setHireContractId(contractId);			//设置合同id
				}
				File imgFile=new File("G:"+File.separator+fileName);
				String[] str=imgFile.list(new ImgFilter(".JPG"));
				batchImg.setImgNum(str.length);						//设置合同图片数量
				batchImg.setLastModifyTime(new Date());				//设置最后修改时间
				batchImg.setIsDel(0);								//设置未删除
				batchImg.setLastModifyTime(new Date());             //加入创建的时间
				bzBatchImgMapper.insertSelective(batchImg);
				point++;
				logger.info("已经读取合同个数========="+point);
			}
		}
	}
	
	
	/**
	 * @description: 图片的上传表示成一个一个任务，每个任务对应一个线程
	 * @param beginId	临时表开始的数据id
	 * @param endId		临时表结束的数据id
	 * @throws Exception
	 * @return void 
	 * @author :ZWenBO
	 */
	@Override
	public void configureTask(Long beginContractId,Long endContractId) throws Exception {
		int threadNum=1;
		long begin = 1;
		String uploadFile="G:"+File.separator;
		long beninTime=System.currentTimeMillis();
		
		while(true){
			List<BzBatchImg> imgs=bzBatchImgMapper.getListBatchImg(begin,begin+threadNum,beginContractId,endContractId);//每次从数据库读取 threadNum个数量的对象
			if(imgs==null || imgs.size()==0){
				long endTime=System.currentTimeMillis();
				logger.info("上传"+(endContractId-beginContractId+1)+"个合同"+"共计耗时=========="+(endTime-beninTime)/1000+"秒");
				return;
			}
			BzBatchImg img=imgs.get(0);
			try {
			    if(img.getImgNum()==0 || img.getHireContractId()==null){
			        
			    }else{
			        uploadService.uploadImgData(uploadFile,img);
			    }
            } catch (Exception e) {
                logger.info("合同ID失败XXXXXXXXXXXXXXXXXXXXXX"+img.getId());
                uploadService.uploadImgFailure(img);                  //调用上传失败的service
            }
			begin=begin+threadNum;//初始偏移量+threadNum
		}
	}  
	
	
	class UploadTask implements Runnable {
		private BzBatchImg img;						//传入的list对象
		private String directory;					//获取图片的目录
		private CountDownLatch latch;				//传入信号量对象
		private UploadImgService service;			//传入service
		
		public UploadTask(BzBatchImg img,String directory,CountDownLatch latch,UploadImgService service) {
			this.img= img;
			this.directory=directory;
			this.latch=latch;
			this.service=service;
		}

		@Override
		public void run() {
			try {
				if(img.getImgNum()==0 || img.getHireContractId()==null){	//改合同是无效的什么都不执行
					
				}else{
					service.uploadImgData(directory, img);
				}
			} catch (Exception e) {
				logger.info("合同ID失败XXXXXXXXXXXXXXXXXXXXXX"+img.getId());
				service.uploadImgFailure(img);					//调用上传失败的service
			}finally {
				logger.info("刷取到合同ID====================="+img.getId());
				latch.countDown();								//信号量减1
			}
		}
	}
	
	
	/**
	 * 过滤合同
	 * @author ZWenBo
	 */
	static class ContractFilter implements FilenameFilter{  
        private String type;  
        public ContractFilter(String type){  
            this.type = type;  
        }  
        public boolean accept(File dir,String name){
        	return name.startsWith(type);
        }  
    }
	
	/**
	 * 过滤图片
	 * @author ZWenBo
	 */
	static class ImgFilter implements FilenameFilter{  
        private String type;  
        public ImgFilter(String type){  
            this.type = type;  
        }  
        public boolean accept(File dir,String name){
        	return name.endsWith(type);
        }  
    }

	
	
	/**
	 * @description: 将directory下的，image文件夹下，已经上传的图片的URL.存储到数据库
	 * @param directory
	 * @param image
	 * @return void 
	 * @author :ZWenBO
	 * @throws Exception 
	 */
	/*@Override
	public void uploadData(String directory,BzBatchImg image) throws Exception{
		int n = 0;
		byte[] bytes = new byte[2048];
		File contractfile=new File(directory+image.getHireContractCode());
		File[] imageFiles = contractfile.listFiles(new ImgFilter(".JPG"));//获取图片对象
		
		for (File mageFile : imageFiles) {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			InputStream input = new FileInputStream(mageFile);
			while (-1 != (n = input.read(bytes))) {
				output.write(bytes, 0, n);
			}
			byte[] picByte = output.toByteArray();
			FileInfoResponse response = storageService.uploadSimple("crm.hireback", "ii",mageFile.getName(), picByte);
			String imgUrl=response.getFile().getUrl();
			
			BzHireAttachment updateImg = new BzHireAttachment();
			
			updateImg.setContractId(image.getHireContractId());
			updateImg.setAreaType("scanProve");
			updateImg.setTerritoryCode(11L);
			updateImg.setIsDel(0);
			updateImg.setImageUrl(imgUrl);
			updateImg.setImageName(mageFile.getName());//补充备件的名称
			updateImg.setIsNecessary(1);//补充备件
			bzHireAttachmentMapper.insertSelective(updateImg);
			
			input.close();
			output.flush();
			output.close();
		};//从合同文件夹下，将图片读分割读取，上传
		
		image.setIsBatch(1);//代表本合同刷数据成功
		bzBatchImgMapper.updateUploadState(image);
	}*/
	
	
	//===============================验证线程与事务的关系===============================================
	@Override
	public void test() {
		BzBatchImg batchImg=new BzBatchImg();
		batchImg.setHireContractCode("test");
		batchImg.setHireContractId(11L);
		batchImg.setImgNum(1);
		batchImg.setIsBatch(1);
		batchImg.setIsDel(0);
		batchImg.setLastModifyTime(new Date());
		bzBatchImgMapper.insert(batchImg);
		logger.info("线程1："+Thread.currentThread().getName()+"==已经执行完毕");
		
		
		new Thread(new Runnable() {
			public void run() {
				/*try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				BatchImg batchImg=new BatchImg();
				batchImg.setContractCode("test");
				batchImg.setContractId(22L);
				batchImg.setImgNum(2);
				batchImg.setIsBatch(2);
				batchImg.setIsDel(2);
				batchImg.setModifyTime(new Date());
				batchImgMapper.insert(batchImg);
				logger.info("线程2："+Thread.currentThread().getName()+"==已经执行完毕");*/
				throw new RuntimeException();
			}
		}).start();
		
		
		new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				BzBatchImg batchImg=new BzBatchImg();
				batchImg.setHireContractCode("test");
				batchImg.setHireContractId(33L);
				batchImg.setImgNum(3);
				batchImg.setIsBatch(3);
				batchImg.setIsDel(3);
				batchImg.setLastModifyTime(new Date());
				bzBatchImgMapper.insert(batchImg);
				logger.info("线程3："+Thread.currentThread().getName()+"==已经执行完毕");
			}
		}).start();
		
		
	}
	//==================================测试事务结束=======================================
}
