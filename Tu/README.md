## LinkDiscoverManager.java
````java
/* Thời gian hết hạn của flow rule được add vào switch,
bằng với một chu kì hết hạn của liên kết giữa các switch */
	public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 15;
	public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 15;

/* Biến lmac lưu giá trí random MAC của chu kì phát gói tin
LLDP hiện tại, hàm getCurrentMac() để service OFSwitchManager
có thể gọi để so sánh MAC Address của gói tin LLDP được handle, 
nếu khác sẽ cảnh báo tấn công. */
	public static MacAddress lmac;
	public static MacAddress getCurrentMac() {
		return lmac;
	}

/* Bảng để lưu các port hợp lệ được nhận gói tin LLDP. Các port này là
các port có gửi gói tin LLDP đến switch khác trong quá trình tìm liên kết. */
	protected static HashMap<String,List<String>> eliPortTable = new HashMap();
	static List<String> listSwPort = new ArrayList<String>();
	List<String> templist = new ArrayList<String>();

/* Phương thức dùng để lấy danh sách các port hợp lệ được phép nhận 
gói tin LLDP hiện tại */
	public static List<String> getValue() {
		return listSwPort;
	}

/* Bảng lưu tần suất nhận LLDP từ switch (để loại bỏ các port down 
giúp tối ưu quá trình gửi LLDP) */
	List<Integer> countList = new ArrayList<Integer>();
	int counttemp = 0;

/* Ghi flow rule cho phép gói tin LLDP có địa chỉ MAC Random 
forward về controller */
	public void writeFlowMod( IOFSwitch sw, MacAddress mac )
	{
		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact( MatchField.ETH_DST, mac );
		mb.setExact( MatchField.ETH_TYPE, EthType.LLDP);
		
		List<OFAction> al = new ArrayList<OFAction>();
		OFAction action = sw.getOFFactory().actions().buildOutput().
							 setPort(OFPort.CONTROLLER).setMaxLen(Integer.MAX_VALUE).build();
		al.add( action );
		OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
		fmb
		.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
		.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
		.setBufferId(OFBufferId.NO_BUFFER)
		.setMatch( mb.build() )
		.setActions( al )
		.setPriority(1000);
		
		sw.write( fmb.build() );
		log.trace("1 flow added to switch {}",sw.getId().toString());
	}

/* Hàm random 1 địa chỉ MAC */
	public static byte[] randomMac() {
		Random rand = new Random();
		byte[] macAddr = new byte[6];
		rand.nextBytes(macAddr);
		macAddr[0] = (byte)(macAddr[0] & (byte)254);
		StringBuilder sb = new StringBuilder(18);
		for(byte b : macAddr){
			if(sb.length() > 0)
				sb.append(":");
			sb.append(String.format("%02x", b));
		}
		String rmac = sb.toString();
		return MacAddress.of(rmac).getBytes();
	}

/* Hàm generate ra gói LLDP mới, thêm tham số đầu vào là mac. 
Gán destinationMACAddress của gói LLDP là tham số mac, 
các phần còn lại tương tụ như hàm generateLLDPMessage */
	public OFPacketOut generateLLDPMessageTilak(IOFSwitch iofSwitch, OFPort port, MacAddress mac, 
					boolean isStandard, boolean isReverse) {
		...(generateLLDPMessage code)
		Ethernet ethernet;
				if (isStandard) {
					ethernet = new Ethernet().setSourceMACAddress(ofpPort.getHwAddr())
							.setDestinationMACAddress(mac)
							.setEtherType(EthType.LLDP);
					ethernet.setPayload(lldp);
				} else {
		...(generateLLDPMessage code)

	/* Thêm đoạn code lọc ra các port hợp lệ mỗi khi gói tin LLDP 
	được nhận về từ switch */
	private Command handleLldp(LLDP lldp, DatapathId sw, OFPort inPort,
				boolean isStandard, FloodlightContext cntx) {
	...
	if (!iofSwitch.portEnabled(inPort)) {
				if (log.isTraceEnabled()) {
					log.trace("Ignoring link with disabled dest port: switch {} port {} {}",
							new Object[] { sw.toString(),
							inPort.getPortNumber(),
							iofSwitch.getPort(inPort).getPortNo().getPortNumber()});
				}
				return Command.STOP;
			}	
	//Code add thêm
	int a = 100;  //Kích thước tối đa của bảng port hợp lệ
	LLDPTLV chId = lldp.getChassisId();
	LLDPTLV portId = lldp.getPortId();
	String ch = chId.toString();
	String po = portId.toString();	    				    				
	String ch1 = ch.replaceAll("type=1 length=7 value=400000", "");
	String po1 = po.replaceAll("type=2 length=3 value=20", "");
	List<String> values = new ArrayList<String>();	    				   				
	values.add(po1);
	eliPortTable.put(ch1,values);
	List <String> listtemp = new ArrayList<String>();
	Iterator<Map.Entry<String,List<String>>> iterator = eliPortTable.entrySet().iterator();
	while (iterator.hasNext())
	{
		String temp = iterator.next().toString();
		// Làm mới danh sách port hợp lệ nếu vượt quá số phần tử
		if (listtemp.size() == a)
		{
			listtemp.clear();
		}
		listtemp.add(temp);	    					
		for (String elements : listtemp)
		{
			boolean check = listSwPort.contains(elements);
			if (check == false)
			{
				/* Thêm các port lấy ra từ các gói LLDP hợp lệ vào
				bảng các port hơp lệ */
				listSwPort.add(elements);
			}				
		}   			
	}

/* Sửa hàm discoverLinks(). Nếu là chu kì đầu tiên thì gửi gói
LLDP đến tất cả các port của switch. Ở những chu kì sau sẽ gửi đến
các port hợp lệ. */
	protected void discoverLinks() {
		// timeout known links.
		timeoutLinks();
		
		//Chu kì đầu
		if (lldpClock == 15) {discoverOnAllPorts();}
		// increment LLDP clock
		lldpClock = (lldpClock + 1) % LLDP_TO_ALL_INTERVAL;

		//Các chu kì sau dùng hàm discoverOnFilterPorts để gửi LLDP
		if (lldpClock == 0 && lldpClock != 15) {
			if (log.isTraceEnabled())
				log.trace("Sending LLDP out on filtered ports.");
			discoverOnFilterPorts();
		}
	}

/* Viết thêm hàm sendDiscoveryMessageTilak(). Thêm tham số đầu vào mac 
để làm tham số cho lời gọi hàm generateLLDPMessageTilak() */
	public boolean sendDiscoveryMessageTilak(DatapathId sw, OFPort port, MacAddress mac,
			boolean isStandard, boolean isReverse) {
		/* ...(sendDiscoveryMessage() code) 
		.................................*/
		return iofSwitch.write(generateLLDPMessageTilak(iofSwitch, port, mac, isStandard, isReverse));
	}

/* Viết thêm hàm discoverOnFilterPorts, dựa trên hàm discoverOnAllPorts() */
	protected void discoverOnFilterPorts() {
		//Random một địa chỉ mac
		MacAddress rmac = MacAddress.of(LinkDiscoveryManager.randomMac());
		//Gan mac random hien tai vao lmac
		lmac = rmac;
	
		// Ghi flow cho phép gói LLDP có destination Mac là rmac
		// forward về controller
		for (DatapathId sw : switchService.getAllSwitchDpids()) {
			IOFSwitch iofSwitch = switchService.getSwitch(sw);
			if (iofSwitch == null) continue;
			if (!iofSwitch.isActive()) continue; /* can't do anything if the switch is SLAVE */
			//this.writeBlock(iofSwitch);	
			this.writeFlowMod(iofSwitch, rmac);
		}
		
		// Xử lí việc gửi gói LLDP đến các port filter trong 
		// bảng port hợp lệ
		System.out.println("Eligible Port Table: ");
		List<String> portTable = LinkDiscoveryManager.getValue();
		System.out.println(portTable);
		for (String obj : portTable) {
			char cId = obj.charAt(0); 
			char cPort = obj.charAt(3);	
			String sId = String.valueOf(cId);
			int iPort = Character.getNumericValue(cPort);
			DatapathId t = DatapathId.of(sId);
			OFPort ofp = OFPort.of(iPort);
			if (isLinkDiscoverySuppressed(t, ofp)) {			
				continue;
			}
			log.trace("Enabled port: {}", ofp);
			log.info("Sending LLDP packet to {} on eligible port {}",t,ofp);
			sendDiscoveryMessageTilak(t, ofp, rmac, true, false);
			NodePortTuple npt = new NodePortTuple(t, ofp);
			addToMaintenanceQueue(npt);
		}
	}
````
## OFSwitchManager,java
* Sửa lại hàm handleMessage, khi có gói tin LLDP packet in đến switch, service OFSwitchManager sẽ kiểm tra nếu destinationMACAddress khác với địa chỉ MAC lưu trong biến lmac lấy từ service LinkDiscoveryManager ở chu kì LLDP hiện tại sẽ cảnh báo tấn công, nếu số gói tin tấn công vượt quá 3000/port/switch thì service OFSwitchManager sẽ gửi tín hiệu đóng port tương ứng để cách li host độc hại ra khỏi mạng, đồng thời ghi lại log tấn công vào file attack.log
````java
 public void handleMessage(IOFSwitchBackend sw, OFMessage m, FloodlightContext bContext) {
        floodlightProvider.handleMessage(sw, m, bContext);
        Ethernet eth = null;
        if (m.getType().toString() == "PACKET_IN") {
        	OFPacketIn pi = (OFPacketIn)m;
        	if (pi.getData().length <= 0) {
        		log.error("Ignoring packet in (Xid = " + pi.getXid() + ") because the data field is empty");
        		return;
        	}
        	else {
        		eth = new Ethernet();
        		eth.deserialize(pi.getData(), 0, pi.getData().length);
        		if (eth.getPayload() instanceof LLDP) {
        	        MacAddress dstmac = eth.getDestinationMACAddress();
        	        if (!dstmac.equals(LinkDiscoveryManager.getCurrentMac())) {
        	        	DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        	        	Date date = new Date();
        	        	String stringPort = pi.getMatch().toString();
        	        	char cOfp = stringPort.charAt(23);
        	        	int iPort = Character.getNumericValue(cOfp);
        	        	OFPort ofp = OFPort.of(iPort);
        	        	log.warn("Detect LLDP attack at switch {} on device with port {}",sw.getId().toString(),ofp);
        	        	String d = df.format(date).toString();
        	        	String t = "[" + d + "]: " + "Detect LLDP attack at switch " + sw.getId().toString() + " on device with port " + pi.getMatch().toString();
        	        	OFSwitch ofsw = (OFSwitch)sw;
        	        	ofsw.increaseAttackCount();
        	        	if (ofsw.acttackCount > 2000) {
        	        		log.warn("Detect LLDP Flooding attack at switch {} port {}",sw.getId().toString(),ofp);
        	        		Set<OFPortConfig> sConfig = EnumSet.noneOf(OFPortConfig.class);
        	        		sConfig.add(OFPortConfig.PORT_DOWN);
        	        		OFPortMod.Builder portDownMod = sw.getOFFactory().buildPortMod()
        	        				.setPortNo(ofp)
        	        				.setConfig(sConfig)
        	        				.setMask(sConfig);
        	        		for (OFPortDesc pd : sw.getPorts()) {
        	        			if (pd.getPortNo().equals(ofp)) {
        	        				portDownMod.setHwAddr(pd.getHwAddr());
        	        				break;
        	        			}
        	        		}
        	        		sw.write(portDownMod.build());
        	        		ofsw.acttackCount = 0;
        	        		log.info("Down port {} on switch {}",ofp,sw.getId().toString());
        	        	}
        	        	try {
	        	        	File file = new File("attack.log");
        	        		FileWriter fw = new FileWriter(file,true);
        	        		BufferedWriter br = new BufferedWriter(fw);
        	        		br.write(t);
        	        		br.newLine();
        	        		br.close();
        	        		fw.close();
        	        	}
        	        	catch (IOException e) {}	
        	        }
        	    }
        	}
        }
    }
````
## OFSwitch.java
````java
/* Thêm thuộc tính trong class OFSwitch để đếm số lần bị tấn công */
protected Integer attackCount;

/* Thêm hàm tăng số lần bị tấn công lên một, được gọi mỗi khi phát hiện 
tấn công từ service OFSwitchManager */
public Integer increaseAttackCount() {
	return this.attackCount++;
}

/* Khởi tạo giá trị 0 cho thuộc tính attackCount trong contructor */
public OFSwitch(...) {
...
	this.attackCount = 0;
	...
}
````
