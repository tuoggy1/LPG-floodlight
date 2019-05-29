# SDN WITH FLOODLIGHT CONTROLLER ENVIRONMENT SETUP 
## 1. Floodlight Installation (Use java8 as your compiler)
* **Cài JAVA**
   *	[download jdk 8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
   *	move file **tar** vào **/usr/lib/jvm**
   ```
	 # cd /usr/lib/jvm
	 # tar xzvf ...tar
	 # nano ~/.bashrc
	 ```
	* Thêm 2 dòng sau:
    >export JAVA_HOME=/usr/lib/jvm/\<folder java đã tar>
      export PATH=\${PATH}:${JAVA_HOME}/bin
      save -> gõ "bash"
     ```
	   # export
	```
   
	* Get source
	````
	  git clone https://github.com/floodlight/floodlight
	 ````
	* Compile floodlight
	````
	  cd floodlight
      git submodule init
      git submodule update
      ant
      mkdir /var/lib/floodlight
      chmod 777 /var/lib/floodlight
     ````

	* Run floodlight
	````
	  cd ~/floodlight
	  java -jar target/floodlight.jar
	 ````
## 2. OpenVSwitch Installation
* From repository
````
	apt-get install openvswitch-switch
````
* Một vài lệnh cơ bản để check thông tin liên quan đến switch (openvswitch)
	*  Xem summary info: 
	````
		ovs-vsctl show
	````
	* Xem chi tiết về các port trên switch: 
	````
		ovs-ofctl show <switch name>, eg. ovs-ofctl show s1
	````
		
	*Xem flows rule của switch: 
	````
		ovs-ofctl dump-flows <switch name>
	````
## 3. Mininet Installation 
* Cài mininet:
````
	apt-get install mininet
	apt-get install xterm (cai them xterm de tuong tac voi host trong mininet)
````
* Kết nối Mininet đến Floodlight controller đã start ở trên
````
	sudo mn --controller=remote,ip=127.0.0.1 --switch=ovsk --topo=linear,3 (topo 3 switch, moi switch co 1 host connect den)
````
* Tương tác với mininet:
````
	mininet> pingall (de test connection giua cac host)
````
>Các host sẽ dùng chung môi trường với OS thật, dùng **xterm** để mở console cho từng đối tượng muốn thao tác lệnh:
>Ví dụ: 
mininet> xterm h1
mininet> xterm h1,h2,h3

* Vào web UI của floodlight để xem thông tin về mạng: http://localhost:8080/ui/index.html
