import 'dart:async';

import 'package:flutter/services.dart';

class ClassroomScreenRecorderConfig {
  bool withOutAudio = false;
  ClassroomScreenRecorderConfig({this.withOutAudio});
  Map<String, dynamic> toMap() {
    return {
      "withOutAudio": withOutAudio ? "1" : "0",
    };
  }
}

class ClassroomScreenRecorder {
  static const MethodChannel _channel = const MethodChannel('classroom_screen_recorder');

  static Future<void> setMethodHandler(Future<dynamic> Function(MethodCall call) handler) async {
    _channel.setMethodCallHandler(handler);
  }

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<Map<String, dynamic>> startScreenRecord({ClassroomScreenRecorderConfig param}) async {
    final Map<String, dynamic> res = (await _channel.invokeMethod('startScreenRecord', param?.toMap()) as Map).cast<String, dynamic>();
    return res;
  }

  static Future<Map<String, dynamic>> stopScreenRecord() async {
    final Map<String, dynamic> res = (await _channel.invokeMethod('stopScreenRecord') as Map).cast<String, dynamic>();
    ;
    return res;
  }
}
