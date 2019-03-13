import 'dart:async';

import 'package:flutter/services.dart';

import 'permission.dart';
import 'location.dart';

class Channel {
  static const location = 'javier-elizaga.location';
  static const locationEvent = 'javier-elizaga.location-event';
}

class Method {
  static const permission = 'permission';
  static const location = 'location';
  static const locationChina = 'locationChina';
  static const request_permissions = "requestPermissions";
  static const is_google_play_available = "isGooglePlayAvailable";
}

class FlutterLocation {
  static const MethodChannel _channel = const MethodChannel(Channel.location);
  static const EventChannel _event = const EventChannel(Channel.locationEvent);
  static Stream<Location> _onLocationChanged;

  static Future<Permission> get permission async {
    String permission = await _channel.invokeMethod(Method.permission);
    return Permission.values.firstWhere(
        (p) => p.toString() == 'Permission.$permission',
        orElse: () => Permission.NOT_DETERMINED);
  }

  static Future<Location> get location async {
    final location = await _channel.invokeMethod(Method.location);
    return Location.fromJson(location);
  }

  static Future<Location> get locationChina async {
    final location = await _channel.invokeMethod(Method.locationChina);
    return Location.fromJson(location);
  }

  static Future<bool> get requestPermission async {
    return await _channel.invokeMethod(Method.request_permissions);
  }

  static Future<bool> get isGooglePlayAvailable async {
    return await _channel.invokeMethod(Method.is_google_play_available);
  }

  static Stream<Location> get onLocationChanged {
    if (_onLocationChanged == null) {
      _onLocationChanged = _event.receiveBroadcastStream().map((data) {
        return Location.fromJson(data);
      });
    }
    return _onLocationChanged;
  }
}
