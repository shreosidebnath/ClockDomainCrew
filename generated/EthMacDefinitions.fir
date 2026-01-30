FIRRTL version 1.2.0
circuit EthMacDefinitions :
  module EthMacDefinitions :
    input clock : Clock
    input reset : UInt<1>

    node _T = cat(UInt<56>("hd5555555555555"), UInt<8>("hfb")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 15:27]
    node _T_1 = cat(UInt<24>("hd55555"), UInt<8>("hfb")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 18:32]
    node _T_2 = cat(UInt<8>("h7"), UInt<8>("h7")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 20:54]
    node _T_3 = cat(_T_2, _T_2) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 20:54]
    node hi = cat(UInt<24>("h555555"), UInt<8>("hfb")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 20:27]
    node _T_4 = cat(hi, _T_3) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 20:27]
    node _T_5 = cat(UInt<8>("h7"), UInt<8>("h7")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 23:59]
    node _T_6 = cat(_T_5, _T_5) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 23:59]
    node hi_1 = cat(UInt<24>("hd55555"), UInt<8>("hfb")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 23:32]
    node _T_7 = cat(hi_1, _T_6) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 23:32]
    node _T_8 = cat(UInt<8>("h7"), UInt<8>("h7")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 28:21]
    node _T_9 = cat(_T_8, _T_8) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 28:21]
    node _T_10 = cat(_T_9, _T_9) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 28:21]
    node _T_11 = cat(UInt<8>("h0"), UInt<8>("h0")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 31:31]
    node _T_12 = cat(_T_11, _T_11) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 31:31]
    node hi_2 = cat(UInt<8>("h0"), _T_11) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 31:31]
    node _T_13 = cat(hi_2, _T_12) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 31:31]
    node _T_14 = cat(_T_13, UInt<8>("hfe")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 31:26]
    node _T_15 = cat(UInt<8>("h0"), UInt<8>("h0")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 34:31]
    node _T_16 = cat(UInt<8>("h0"), _T_15) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 34:31]
    node _T_17 = cat(UInt<8>("h0"), UInt<8>("h0")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 34:53]
    node _T_18 = cat(_T_17, _T_17) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 34:53]
    node hi_3 = cat(_T_16, UInt<8>("hfe")) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 34:26]
    node _T_19 = cat(hi_3, _T_18) @[\\src\\main\\scala\\ethernet\\MacDefinitions.scala 34:26]

