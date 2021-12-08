#import "ClassroomScreenRecorderPlugin.h"
#import <AVFoundation/AVFoundation.h>
#import <ReplayKit/ReplayKit.h>
#import <Photos/Photos.h>
@interface ClassroomScreenRecorderPlugin()
@property(nonatomic,assign)BOOL withOutAudio;;
@property(nonatomic,copy)NSURL *outputUrl;
@property(nonatomic,strong)AVAssetWriterInput *audioInput;
@property(nonatomic,strong)AVAssetWriterInput *videoWriterInput;
@property(nonatomic,strong)AVAssetWriter *videoWriter;
 
@end

@implementation ClassroomScreenRecorderPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  FlutterMethodChannel* channel = [FlutterMethodChannel
      methodChannelWithName:@"classroom_screen_recorder"
            binaryMessenger:[registrar messenger]];
  ClassroomScreenRecorderPlugin* instance = [[ClassroomScreenRecorderPlugin alloc] init];
  [registrar addMethodCallDelegate:instance channel:channel];
}

- (void)resetSet{
    self.withOutAudio = NO;
}
- (void)startRecord:(FlutterMethodCall *)call result:(FlutterResult)result{
    [self resetSet];
    if([call.arguments isKindOfClass:[NSDictionary class]])
    {
        NSDictionary *dict = call.arguments;
        if([dict[@"withOutAudio"] isEqual:@"1"])
        {
            self.withOutAudio = YES;
        }
    }
    NSTimeInterval time =  [[NSDate date] timeIntervalSince1970];
    NSString *documentPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory,NSUserDomainMask,YES)lastObject];
    NSInteger ntime = [@(time) integerValue];
    NSString *path = [documentPath stringByAppendingPathComponent:[NSString stringWithFormat:@"clasroom_recoder_%@.mp4",@(ntime)]];
    self.outputUrl = [NSURL fileURLWithPath:path];
    [[NSFileManager defaultManager] removeItemAtPath:path error:NULL];
    
    self.videoWriter = [[AVAssetWriter alloc] initWithURL:self.outputUrl fileType:AVFileTypeMPEG4 error:nil];

    if (@available(iOS 11.0, *)) {
        [[RPScreenRecorder sharedRecorder] setMicrophoneEnabled:!self.withOutAudio];
        self.videoWriterInput = [[AVAssetWriterInput alloc] initWithMediaType:AVMediaTypeVideo outputSettings:@{
            
            AVVideoCodecKey:AVVideoCodecH264,
            AVVideoWidthKey:@([UIScreen mainScreen].bounds.size.width),
            AVVideoHeightKey:@([UIScreen mainScreen].bounds.size.height),
            
        }];
        self.videoWriterInput.expectsMediaDataInRealTime = true;
        [self.videoWriter addInput:self.videoWriterInput];
        if(!self.withOutAudio){
           self.audioInput =[[AVAssetWriterInput alloc] initWithMediaType:AVMediaTypeAudio outputSettings:@{
               AVNumberOfChannelsKey:@(2),
               AVFormatIDKey:@(kAudioFormatMPEG4AAC),
               AVSampleRateKey:@(44100),
               AVEncoderAudioQualityKey:@(AVAudioQualityHigh),
           }];
           self.audioInput.expectsMediaDataInRealTime = true;
           [self.videoWriter addInput:self.audioInput];
        }
        [[RPScreenRecorder sharedRecorder] startCaptureWithHandler:^(CMSampleBufferRef  _Nonnull sampleBuffer, RPSampleBufferType bufferType, NSError * _Nullable error) {
            
            if(bufferType == RPSampleBufferTypeVideo)
            {
                
                NSLog(@"Writing video...");
                if(self.videoWriter.status == AVAssetWriterStatusUnknown)
                {
                    
                    result(@{
                            @"res":@"success",
                           });
                    [self.videoWriter startWriting];
                    [self.videoWriter startSessionAtSourceTime: CMSampleBufferGetPresentationTimeStamp(sampleBuffer)];
                    
                }else if(self.videoWriter.status == AVAssetWriterStatusWriting )
                {
                    if(self.videoWriterInput.isReadyForMoreMediaData)
                    {
                        NSLog(@"append video...");
                        bool res =  [self.videoWriterInput appendSampleBuffer:sampleBuffer];
                        if(!res)
                        {
                            result(@{
                                @"res":@"error",
                                    @"msg":@"Problems writing video",
                            });
                        }
                    }
                }
               
            } else if(bufferType == RPSampleBufferTypeAudioMic )
            {
                
                if(!self.withOutAudio){
                    NSLog(@"Writing audio....");
                    if(self.audioInput.isReadyForMoreMediaData){
                        bool res = [self.audioInput appendSampleBuffer:sampleBuffer];
                        if(!res)
                        {
                            NSLog(@"Problems writing audio");
                        }
                    }
               
                }
            }
            if (CMSampleBufferDataIsReady(sampleBuffer) && bufferType == RPSampleBufferTypeVideo) {
                NSLog(@"Recording started successfully.");
                //save 屏幕数据
            }
        } completionHandler:^(NSError * _Nullable error) {
            if(!error)
            {
                result(@{
                @"res":@"success",
                
               });
                
            }else{
                result(@{
                @"res":@"error",
                
               });
            }
        }];
           
    } else {
        result(@{
                @"res":@"error",
                @"msg":@"ios version must >= 11.0"
               });
    }

 
    return ;
}
- (NSDictionary *)endRecord:(FlutterMethodCall *)call result:(FlutterResult)result{
    if (@available(iOS 11.0, *)) {
        [[RPScreenRecorder sharedRecorder] stopCaptureWithHandler:^(NSError * _Nullable error) {
            
          
        }];
 
        [self.videoWriterInput markAsFinished];
        if(!self.withOutAudio)
        {
            [self.audioInput markAsFinished];
        }
        [self.videoWriter finishWritingWithCompletionHandler:^{
            __block PHObjectPlaceholder *placeholder;

                [[PHPhotoLibrary sharedPhotoLibrary] performChanges:^{
                    PHAssetChangeRequest* createAssetRequest = [PHAssetChangeRequest creationRequestForAssetFromVideoAtFileURL:self.outputUrl];
                    placeholder = [createAssetRequest placeholderForCreatedAsset];
                        
                } completionHandler:^(BOOL success, NSError *error) {
                    if (success)
                    {
                       NSLog(@"didFinishRecordingToOutputFileAtURL - success for ios9");
                        result(@{
                                @"res":@"success",
                                @"file":[self.outputUrl path],
                               });
                    }
                    else
                    {
                        result(@{
                            @"res":@"error",
                            @"file":[self.outputUrl path],
                            @"msg":@"stop unknow error"
                           });
                       
                        NSLog(@"%@", error);
                    }
                }];
           
        }];
        
    }else{
        result(@{
                @"res":@"error",
                @"msg":@"ios version must >= 11.0"
               });
    }
    return nil;
}
- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
  if ([@"getPlatformVersion" isEqualToString:call.method]) {
    result([@"iOS " stringByAppendingString:[[UIDevice currentDevice] systemVersion]]);
  }else if([@"startScreenRecord" isEqualToString:call.method])
  {
      [self startRecord:call result:(FlutterResult)result];

  }else if([@"stopScreenRecord" isEqualToString:call.method])
  {  
      [self endRecord:call result:(FlutterResult)result];
  }else {
    result(FlutterMethodNotImplemented);
  }
}
 
@end
