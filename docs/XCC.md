文档：[https://docs.xswitch.cn/xcc-api/api/#dial](https://docs.xswitch.cn/xcc-api/api/#dial)

# 呼入流程
## 来电事件
### 通话开始 start
```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"uuid":	"7786dd6f-e7a8-48e7-bf5c-378574eae80c",
		"state":	"START",
		"domain":	"ccx.carrierglobe.com",
		"cid_name":	"13262857336",
		"cid_number":	"13262857336",
		"dest_number":	"31923070",
		"bridged":	false,
		"answered":	false,
		"hold":	false,
		"video":	false,
		"direction":	"inbound",
		"create_epoch":	1751879466,
		"ring_epoch":	0,
		"context":	"context-3",
		"params":	{
			"create_epoch":	"1751879466",
			"ring_epoch":	"0"
		}
	}
}
```

### 应答指令 answer
文档：[https://docs.xswitch.cn/xcc-api/api/#answer](https://docs.xswitch.cn/xcc-api/api/#answer)

```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"uuid":	"7786dd6f-e7a8-48e7-bf5c-378574eae80c",
		"state":	"ANSWERED",
		"domain":	"ccx.carrierglobe.com",
		"cid_name":	"13262857336",
		"cid_number":	"13262857336",
		"dest_number":	"31923070",
		"direction":	"inbound",
		"answered":	true,
		"params":	{
		}
	}
}
```

## 导航语音指令
文档：[https://docs.xswitch.cn/xcc-api/api/#readdtmf](https://docs.xswitch.cn/xcc-api/api/#readdtmf)

```json
{
  "jsonrpc": "2.0",
  "id": "a3094ddd-d58a-45f0-9a23-b18c034e7bcb",
  "method": "XNode.ReadDTMF",
  "params": {
    "digit_timeout": 2000,
    "min_digits": 1,
    "terminators": "#",
    "media": {
      "voice": "aiqi",
      "data": "您好，欢迎致电鸭嘴兽，派车问题请按1，其它问题请按2，重播请按0，以井号键结束！",
      "engine": "ali",
      "type": "TEXT"
    },
    "uuid": "7786dd6f-e7a8-48e7-bf5c-378574eae80c",
    "max_digits": 1,
    "timeout": 5000
  }
}
```

### XSwitch应答
```json
{
	"jsonrpc":	"2.0",
	"id":	"a3094ddd-d58a-45f0-9a23-b18c034e7bcb",
	"result":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"code":	202,
		"message":	"OK",
		"uuid":	"7786dd6f-e7a8-48e7-bf5c-378574eae80c",
		"dtmf":	"1"
	}
}
```

## 桥接指令
文档：[https://docs.xswitch.cn/xcc-api/api/#bridge](https://docs.xswitch.cn/xcc-api/api/#bridge)

```json
{
  "jsonrpc": "2.0",
  "id": "b6d5f79b-6194-4dff-ac5d-e3e75de01203",
  "method": "XNode.Bridge",
  "params": {
    "ringback": "%(1000,4000,450)",
    "destination": {
      "call_params": [
        {
          "dial_string": "user/800035",
          "cid_name": "13262857336",
          "cid_number": "13262857336",
          "params": {
            "absolute_codec_string": "PCMA,G722,G729,OPUS",
            "xcc_no_cid_flip": "true"
          },
          "uuid": "3e4e72c8-c5ff-4180-977e-8a0648b1e984"
        }
      ],
      "global_params": {
        "ignore_early_media": "false"
      }
    },
    "uuid": "7786dd6f-e7a8-48e7-bf5c-378574eae80c",
    "flow_control": "NONE",
    "ctrl_uuid": "9416f93e-13af-47ef-bb4b-600b72580706"
  }
}
```

### XSwitch应答
如果坐席接听，将收到

```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"uuid":	"3e4e72c8-c5ff-4180-977e-8a0648b1e984",
		"peer_uuid":	"7786dd6f-e7a8-48e7-bf5c-378574eae80c",
		"state":	"BRIDGE",
		"domain":	"ccx.carrierglobe.com"
	}
}
```

```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"uuid":	"7786dd6f-e7a8-48e7-bf5c-378574eae80c",
		"peer_uuid":	"3e4e72c8-c5ff-4180-977e-8a0648b1e984",
		"state":	"BRIDGE",
		"domain":	"ccx.carrierglobe.com"
	}
}
```

如果坐席没有接听，将会收到通话销毁事件。

### 通话结束
如果坐席接听，通话结束之后坐席挂断，将收到

```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"uuid":	"7786dd6f-e7a8-48e7-bf5c-378574eae80c",
		"peer_uuid":	"3e4e72c8-c5ff-4180-977e-8a0648b1e984",
		"state":	"UNBRIDGE",
		"domain":	"ccx.carrierglobe.com"
	}
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"3cb2712d-9355-48db-96a9-9026d0cf5452",
	"result":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"uuid":	"7786dd6f-e7a8-48e7-bf5c-378574eae80c",
		"code":	200,
		"message":	"OK"
	}
}
```

## 通话录音
文档：[https://docs.xswitch.cn/xcc-api/api/#record](https://docs.xswitch.cn/xcc-api/api/#record)

### 录音开始
```json
{
  "jsonrpc": "2.0",
  "id": "febc3e7b-c5a7-4e7b-b41e-75427e53e099",
  "method": "XNode.Record",
  "params": {
    "path": "/opt/data/recordings/2025-7/record-2025-7-7-34a40efb-19b0-4695-8028-534c32928b9b.wav",
    "action": "START",
    "uuid": "34a40efb-19b0-4695-8028-534c32928b9b",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

XSwitch应答

```json
{
	"jsonrpc":	"2.0",
	"id":	"febc3e7b-c5a7-4e7b-b41e-75427e53e099",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"34a40efb-19b0-4695-8028-534c32928b9b",
		"code":	200,
		"message":	"OK"
	}
}
```

### 录音结束
```json
{
  "jsonrpc": "2.0",
  "id": "c901c49b-9768-4f03-b817-8118113db667",
  "method": "XNode.Record",
  "params": {
    "path": "/opt/data/recordings/2025-7/record-2025-7-7-34a40efb-19b0-4695-8028-534c32928b9b.wav",
    "action": "STOP",
    "uuid": "34a40efb-19b0-4695-8028-534c32928b9b",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

XSwitch应答

```json
{
	"jsonrpc":	"2.0",
	"id":	"c901c49b-9768-4f03-b817-8118113db667",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"34a40efb-19b0-4695-8028-534c32928b9b",
		"code":	200,
		"message":	"OK"
	}
}
```

## 服务评价指令
```json
{
  "jsonrpc": "2.0",
  "id": "106d8597-f15f-4adf-b3c5-77499e6d1973",
  "method": "XNode.ReadDTMF",
  "params": {
    "digit_timeout": 2000,
    "min_digits": 1,
    "terminators": "#",
    "media": {
      "voice": "aiqi",
      "data": "您好，请给我们的服务做出评价，非常满意请按1，满意请按2，不满意请按3，非常不满意请按4，重播请按0！以井号键结束",
      "engine": "ali",
      "type": "TEXT"
    },
    "uuid": "34a40efb-19b0-4695-8028-534c32928b9b",
    "max_digits": 1,
    "timeout": 5000
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"106d8597-f15f-4adf-b3c5-77499e6d1973",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"code":	202,
		"message":	"OK",
		"uuid":	"34a40efb-19b0-4695-8028-534c32928b9b",
		"dtmf":	"2"
	}
}
```

## 再见
文档：[https://docs.xswitch.cn/xcc-api/api/#play](https://docs.xswitch.cn/xcc-api/api/#play)

```json
{
  "jsonrpc": "2.0",
  "id": "f7753e7b-1a7e-47bd-bcf1-17d5e2f525af",
  "method": "XNode.Play",
  "params": {
    "media": {
      "voice": "aiqi",
      "data": "感谢您的评价，再见",
      "engine": "ali",
      "type": "TEXT"
    },
    "uuid": "34a40efb-19b0-4695-8028-534c32928b9b",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"f7753e7b-1a7e-47bd-bcf1-17d5e2f525af",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"34a40efb-19b0-4695-8028-534c32928b9b",
		"code":	200,
		"message":	"OK"
	}
}
```

## 挂断
文档：[https://docs.xswitch.cn/xcc-api/api/#hangup](https://docs.xswitch.cn/xcc-api/api/#hangup)

```json
{
  "jsonrpc": "2.0",
  "id": "ca69a683-f225-499c-8f25-18f096e5c638",
  "method": "XNode.Hangup",
  "params": {
    "cause": "NORMAL_CLEARING",
    "uuid": "34a40efb-19b0-4695-8028-534c32928b9b",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"ca69a683-f225-499c-8f25-18f096e5c638",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"34a40efb-19b0-4695-8028-534c32928b9b",
		"code":	200,
		"message":	"OK"
	}
}
```

# 呼出流程
## 呼叫坐席的话机
```json
{
  "jsonrpc": "2.0",
  "id": "07c4d171-aafa-4b7f-ba03-0269a2dfa5f2",
  "method": "XNode.Dial",
  "params": {
    "ringback": "%(1000,4000,450)",
    "destination": {
      "call_params": [
        {
          "dial_string": "user/2000",
          "cid_name": "钱丁君",
          "cid_number": "18762696252",
          "params": {
            "absolute_codec_string": "PCMA,G722,G729,OPUS",
            "xcc_no_cid_flip": "true"
          },
          "uuid": "21ae54b1-c684-470a-95e1-ce5ebb4a0412"
        }
      ],
      "global_params": {
        "ignore_early_media": "false"
      }
    },
    "sync": true,
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"07c4d171-aafa-4b7f-ba03-0269a2dfa5f2",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"code":	200,
		"message":	"OK",
		"cause":	"SUCCESS",
		"uuid":	"21ae54b1-c684-470a-95e1-ce5ebb4a0412"
	}
}
```

## 呼叫手机号码
```json
{
  "jsonrpc": "2.0",
  "id": "58882d34-5181-4b77-9576-49384c094e0a",
  "method": "XNode.Dial",
  "params": {
    "ringback": "%(1000,4000,450)",
    "destination": {
      "call_params": [
        {
          "dial_string": "sofia/gateway/gwm/018762696252",
          "cid_name": "钱丁君",
          "cid_number": "31923085",
          "params": {
            "absolute_codec_string": "PCMA,G722,G729,OPUS",
            "xcc_no_cid_flip": "true"
          },
          "uuid": "4c753843-3871-405d-9f81-9a71581d35c2"
        }
      ],
      "global_params": {
        "ignore_early_media": "false"
      }
    },
    "sync": true,
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"58882d34-5181-4b77-9576-49384c094e0a",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"code":	200,
		"message":	"OK",
		"cause":	"SUCCESS",
		"uuid":	"4c753843-3871-405d-9f81-9a71581d35c2"
	}
}
```

## 桥接
```json
{
  "jsonrpc": "2.0",
  "id": "c3ae2033-0c41-4dce-ade7-0182553cfd13",
  "method": "XNode.ChannelBridge",
  "params": {
    "uuid": "4c753843-3871-405d-9f81-9a71581d35c2",
    "flow_control": "NONE",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9",
    "peer_uuid": "21ae54b1-c684-470a-95e1-ce5ebb4a0412"
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"c3ae2033-0c41-4dce-ade7-0182553cfd13",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"4c753843-3871-405d-9f81-9a71581d35c2",
		"code":	200,
		"message":	"OK"
	}
}
```

## 录音
### 录音开始
```json
{
  "jsonrpc": "2.0",
  "id": "634b0deb-cd09-44df-af14-024ea23ece40",
  "method": "XNode.Record",
  "params": {
    "path": "/opt/data/recordings/2025-7/record-2025-7-7-4c753843-3871-405d-9f81-9a71581d35c2.wav",
    "action": "START",
    "uuid": "4c753843-3871-405d-9f81-9a71581d35c2",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"634b0deb-cd09-44df-af14-024ea23ece40",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"4c753843-3871-405d-9f81-9a71581d35c2",
		"code":	200,
		"message":	"OK"
	}
}
```

### 录音结束
```json
{
  "jsonrpc": "2.0",
  "id": "1c579fca-8535-4acb-9165-46fd7eaf442f",
  "method": "XNode.Record",
  "params": {
    "path": "/opt/data/recordings/2025-7/record-2025-7-7-4c753843-3871-405d-9f81-9a71581d35c2.wav",
    "action": "STOP",
    "uuid": "4c753843-3871-405d-9f81-9a71581d35c2",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"1c579fca-8535-4acb-9165-46fd7eaf442f",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"4c753843-3871-405d-9f81-9a71581d35c2",
		"code":	200,
		"message":	"OK"
	}
}
```

## 服务评价
```json
{
  "jsonrpc": "2.0",
  "id": "9e20a377-86ae-4628-95bc-6ad69ea60324",
  "method": "XNode.ReadDTMF",
  "params": {
    "digit_timeout": 2000,
    "min_digits": 1,
    "terminators": "#",
    "media": {
      "voice": "aiqi",
      "data": "您好，请给我们的服务做出评价，非常满意请按1，满意请按2，不满意请按3，非常不满意请按4，重播请按0！以井号键结束",
      "engine": "ali",
      "type": "TEXT"
    },
    "uuid": "4c753843-3871-405d-9f81-9a71581d35c2",
    "max_digits": 1,
    "timeout": 5000
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"9e20a377-86ae-4628-95bc-6ad69ea60324",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"code":	202,
		"message":	"OK",
		"uuid":	"4c753843-3871-405d-9f81-9a71581d35c2",
		"dtmf":	"2"
	}
}
```

## 再见
```json
{
  "jsonrpc": "2.0",
  "id": "8fc1a04d-60ed-41f2-996a-ab20392b3d5a",
  "method": "XNode.Play",
  "params": {
    "media": {
      "voice": "aiqi",
      "data": "感谢您的评价，再见",
      "engine": "ali",
      "type": "TEXT"
    },
    "uuid": "4c753843-3871-405d-9f81-9a71581d35c2",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"8fc1a04d-60ed-41f2-996a-ab20392b3d5a",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"4c753843-3871-405d-9f81-9a71581d35c2",
		"code":	200,
		"message":	"OK"
	}
}
```

## 挂断
```json
{
  "jsonrpc": "2.0",
  "id": "1c8c1d23-819b-456a-8259-d98436f4f57f",
  "method": "XNode.Hangup",
  "params": {
    "cause": "NORMAL_CLEARING",
    "uuid": "4c753843-3871-405d-9f81-9a71581d35c2",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

```json
{
	"jsonrpc":	"2.0",
	"id":	"1c8c1d23-819b-456a-8259-d98436f4f57f",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"4c753843-3871-405d-9f81-9a71581d35c2",
		"code":	200,
		"message":	"OK"
	}
}
```

# 转接
文档：[https://docs.xswitch.cn/xcc-api/api/#transfer](https://docs.xswitch.cn/xcc-api/api/#transfer)

呼叫需要转接的坐席的接听设备

```json
{
  "jsonrpc": "2.0",
  "id": "1571c471-2112-4b74-ab83-b627e20eabbe",
  "method": "XNode.Dial",
  "params": {
    "ringback": "%(1000,4000,450)",
    "destination": {
      "call_params": [
        {
          "dial_string": "user/2013",
          "cid_name": "钱丁君",
          "cid_number": "901399",
          "params": {
            "absolute_codec_string": "PCMA,G722,G729,OPUS",
            "xcc_no_cid_flip": "true"
          },
          "uuid": "fe4752c5-3918-419d-bec2-93d2c8fc4780"
        }
      ],
      "global_params": {
        "ignore_early_media": "false"
      }
    },
    "sync": true,
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

## 振铃声
leg A转到振铃声

```json
{
  "jsonrpc": "2.0",
  "id": "1571c471-2112-4b74-ab83-b627e20eabbe",
  "method": "XNode.NativeAPI",
  "params": {
    "args": "6dea8f71-90e9-4b37-b511-ded3a8f0506b -both 1991991 XML context-1",
    "cmd": "uuid_transfer",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

leg B转到响铃声

```json
{
  "jsonrpc": "2.0",
  "id": "1571c471-2112-4b74-ab83-b627e20eabbe",
  "method": "XNode.NativeAPI",
  "params": {
    "args": "8dd0e37b-03a2-4a1c-b2f5-e4728ac82c77 -both 1991991 XML context-1",
    "cmd": "uuid_transfer",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9"
  }
}
```

## XSwitch响应
```json
{
	"jsonrpc":	"2.0",
	"id":	"1571c471-2112-4b74-ab83-b627e20eabbe",
	"result":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"code":	200,
		"message":	"OK",
		"cause":	"SUCCESS",
		"uuid":	"fe4752c5-3918-419d-bec2-93d2c8fc4780"
	}
}
```

## 桥接
```json
{
  "jsonrpc": "2.0",
  "id": "1571c471-2112-4b74-ab83-b627e20eabbe",
  "method": "XNode.ChannelBridge",
  "params": {
    "contine_on_fail": false,
    "uuid": "6dea8f71-90e9-4b37-b511-ded3a8f0506b",
    "flow_control": "NONE",
    "ctrl_uuid": "affcac24-ff91-4bd3-bee6-4ee148c575a9",
    "peer_uuid": "fe4752c5-3918-419d-bec2-93d2c8fc4780"
  }
}
```

# 通话状态
<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2025/png/25758417/1752032001121-a9388653-8a01-49e5-9be4-4f07e9e08b46.png)

## 开始 START
来话第一个事件

```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"34a40efb-19b0-4695-8028-534c32928b9b",
		"state":	"START",
		"domain":	"ccx.fat.driverglobe.com",
		"cid_name":	"18762696252",
		"cid_number":	"18762696252",
		"dest_number":	"31457415",
		"bridged":	false,
		"answered":	false,
		"hold":	false,
		"video":	false,
		"direction":	"inbound",
		"create_epoch":	1751881093,
		"ring_epoch":	0,
		"context":	"context-3",
		"params":	{
			"create_epoch":	"1751881093",
			"ring_epoch":	"0"
		}
	}
}
```

## 呼叫 CALLING
去话第一个事件

```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"uuid":	"3e4e72c8-c5ff-4180-977e-8a0648b1e984",
		"state":	"CALLING",
		"domain":	"ccx.carrierglobe.com",
		"cid_name":	"13262857336",
		"cid_number":	"13262857336",
		"dest_number":	"800035",
		"direction":	"outbound",
		"answered":	false,
		"params":	{
		}
	}
}
```

## 振铃 RINGING
```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"uuid":	"3e4e72c8-c5ff-4180-977e-8a0648b1e984",
		"state":	"RINGING",
		"domain":	"ccx.carrierglobe.com",
		"cid_name":	"13262857336",
		"cid_number":	"13262857336",
		"dest_number":	"800035",
		"direction":	"outbound",
		"answered":	false,
		"params":	{
		}
	}
}
```

## 应答 ANSWERED
```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"uuid":	"3e4e72c8-c5ff-4180-977e-8a0648b1e984",
		"state":	"ANSWERED",
		"domain":	"ccx.carrierglobe.com",
		"cid_name":	"13262857336",
		"cid_number":	"13262857336",
		"dest_number":	"800035",
		"direction":	"outbound",
		"answered":	true,
		"params":	{
		}
	}
}
```

## 媒体 MEDIA
```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"uuid":	"3e4e72c8-c5ff-4180-977e-8a0648b1e984",
		"state":	"MEDIA",
		"domain":	"ccx.carrierglobe.com",
		"cid_name":	"13262857336",
		"cid_number":	"13262857336",
		"dest_number":	"800035",
		"direction":	"outbound",
		"answered":	true,
		"params":	{
		}
	}
}
```

## 就绪 READY
```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"21ae54b1-c684-470a-95e1-ce5ebb4a0412",
		"state":	"READY",
		"domain":	"ccx.fat.driverglobe.com",
		"cid_name":	"2000",
		"cid_number":	"18762696252",
		"dest_number":	"2000",
		"direction":	"outbound",
		"answered":	true,
		"params":	{
		}
	}
}
```

## 桥接 BRIDGE
```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"21ae54b1-c684-470a-95e1-ce5ebb4a0412",
		"peer_uuid":	"4c753843-3871-405d-9f81-9a71581d35c2",
		"state":	"BRIDGE",
		"domain":	"ccx.fat.driverglobe.com"
	}
}
```

## 断开桥接 UNBRIDGE
```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"f2abd9ec-403b-42e9-923d-3df3d1d41898",
		"uuid":	"21ae54b1-c684-470a-95e1-ce5ebb4a0412",
		"peer_uuid":	"4c753843-3871-405d-9f81-9a71581d35c2",
		"state":	"UNBRIDGE",
		"domain":	"ccx.fat.driverglobe.com"
	}
}
```

##  挂机 DESTROY
```json
{
	"jsonrpc":	"2.0",
	"method":	"Event.Channel",
	"params":	{
		"node_uuid":	"52762c74-5deb-4cd2-bebc-4e02f380c174",
		"node_ip":	"10.2.14.14",
		"uuid":	"3e4e72c8-c5ff-4180-977e-8a0648b1e984",
		"state":	"DESTROY",
		"domain":	"ccx.carrierglobe.com",
		"duration":	"22",
		"billsec":	"16",
		"cause":	"NORMAL_CLEARING",
		"direction":	"outbound",
		"answered":	true,
		"params":	{
		}
	}
}
```

