import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:classroom_screen_recorder/classroom_screen_recorder.dart';

void main() {
  const MethodChannel channel = MethodChannel('classroom_screen_recorder');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await ClassroomScreenRecorder.platformVersion, '42');
  });
}
