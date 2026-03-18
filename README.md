# grpc

* first: 熟悉etcd
* second: 註冊服務到etcd並進行定期續約
* third: 獲取etcd的服務
* fifth-api: 
  * 將proto文件放入到 src/main/proto目錄
  * maven插件
  * 執行maven clean compile, 在target/generated-sources/protobuf目錄生成文件
* fifth-server:
  * 引入fifth-api依賴,並且引入 io.github.lognet[grpc-spring-boot-starter]-> 配置文件指定grpc端口配置: grpc.port(坑)
  * 註冊服務並定期續約
* fifth-client
  * 引入fifth-api依賴
  * 實現服務發現並且監聽更新
  