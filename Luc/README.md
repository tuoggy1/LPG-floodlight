# NEW FLOODLIGHT LLDP MECHANISM
* Khai báo thêm các thuộc tính cần thiết trong class LinkDiscoveryManager,java
````java
public class LinkDiscoveryManager implements IOFMessageListener,
IOFSwitchListener, IStorageSourceListener, ILinkDiscoveryService,
IFloodlightModule, IInfoProvider {
	//FlowModClass elements
	public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 15;
	public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 15;
	//EligiblePort Table
	static List<String> listSwPort = new ArrayList<String>();
	List<String> templist = new ArrayList<String>();
	//Biến random Mac tại mỗi chu kì LLDP, cho phép truy cập từ các class khác
	public static MacAddress lmac;
````
* Khai báo các phương thức cần thiết
````java
//Hàm lấy giá trị của EligiblePort Table hiện tại
public static List<String> getValue()
{
	return listSwPort;
}
//Hàm lấy giá trị của lmac hiện tại (dùng để truy xuất giá trị random MAC tại chu kì LLDP hiện tại từ các class khác)
public static MacAddress getCurrentMac() {
	return lmac;
}
//Hàm add flow drop các gói unknown LLDP
public void writeBlock( IOFSwitch sw )
{
	// generate a Match Filter
	Match.Builder mb = sw.getOFFactory().buildMatch();
	mb.setExact( MatchField.ETH_TYPE, EthType.LLDP);
	// generate an action list
	List<OFAction> al = new ArrayList<OFAction>();
	OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
	fmb
	.setHardTimeout(0)	 //infinity time out
	.setIdleTimeout(0)	 //infinity time out
	.setBufferId(OFBufferId.NO_BUFFER)
	.setMatch( mb.build() )
	.setActions( al )
	.setPriority(500);   //độ ưu tiên cao hơn rule mặc định
	// đẩy tất cả các gói tin không match flow nào về controller(là 0)
	// finally write it out to switch
	sw.write( fmb.build() );
	log.trace("Block unknown LLDP packets on switch {}",sw.getId().toString());
// Hàm add flow cho phép gói LLDP có chứa destination MAC Address cụ thể về controller
public void writeFlowMod( IOFSwitch sw, MacAddress mac )
{
	// generate a Match Filter
	Match.Builder mb = sw.getOFFactory().buildMatch();
	mb.setExact( MatchField.ETH_DST, mac );
	mb.setExact( MatchField.ETH_TYPE, EthType.LLDP);
	// generate an action list
	List<OFAction> al = new ArrayList<OFAction>();
	// generate a port and table id instance
	OFAction action = sw.getOFFactory().actions().buildOutput().
	setPort(OFPort.CONTROLLER).setMaxLen(Integer.MAX_VALUE).build();
	al.add( action );
	// generate and start to build an OFFlowMod Message
	OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
	fmb
	.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
	.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
	.setBufferId(OFBufferId.NO_BUFFER)
	.setMatch( mb.build() )
	.setActions( al )
	.setPriority(1000);  // độ ưu tiên cao hơn với rule block
	// finally write it out to switch
	sw.write( fmb.build() );
	log.trace("1 flow added to switch {}",sw.getId().toString());
}
//Hàm random địa chỉ MAC
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
````
* Sửa lại hàm generateLLDPMessage (bằng cách tạo mới hàm generateLLDPMessageTilak
````java
public OFPacketOut generateLLDPMessageTilak(IOFSwitch iofSwitch, OFPort port, MacAddress mac,
boolean isStandard, boolean isReverse) {
	OFPortDesc ofpPort = iofSwitch.getPort(port);
	if (log.isTraceEnabled()) {
		log.trace("Sending LLDP packet out of swich: {}, port: {}, reverse: {}",
		new Object[] {iofSwitch.getId().toString(), port.toString(), Boolean.toString(isReverse)});
	}

	// using "nearest customer bridge" MAC address for broadest possible
	// propagation
	// through provider and TPMR bridges (see IEEE 802.1AB-2009 and
	// 802.1Q-2011),
	// in particular the Linux bridge which behaves mostly like a provider
	// bridge

	byte[] chassisId = new byte[] { 4, 0, 0, 0, 0, 0, 0 }; // filled in

	// later

	byte[] portId = new byte[] { 2, 0, 0 }; // filled in later
	byte[] ttlValue = new byte[] { 0, 0x78 };

	// OpenFlow OUI - 00-26-E1-00

	byte[] dpidTLVValue = new byte[] { 0x0, 0x26, (byte) 0xe1, 0, 0, 0,
	0, 0, 0, 0, 0, 0 };

	LLDPTLV dpidTLV = new LLDPTLV().setType((byte) 127)
	.setLength((short) dpidTLVValue.length)
	.setValue(dpidTLVValue);

	byte[] dpidArray = new byte[8];

	ByteBuffer dpidBB = ByteBuffer.wrap(dpidArray);

	ByteBuffer portBB = ByteBuffer.wrap(portId, 1, 2);

	DatapathId dpid = iofSwitch.getId();

	dpidBB.putLong(dpid.getLong());

	// set the chassis id's value to last 6 bytes of dpid

	System.arraycopy(dpidArray, 2, chassisId, 1, 6);

	// set the optional tlv to the full dpid

	System.arraycopy(dpidArray, 0, dpidTLVValue, 4, 8);

	// TODO: Consider remove this block of code.
	// It's evil to overwrite port object. The the old code always
	// overwrote mac address, we now only overwrite zero macs and
	// log a warning, mostly for paranoia.

	byte[] srcMac = ofpPort.getHwAddr().getBytes();
	byte[] zeroMac = { 0, 0, 0, 0, 0, 0 };
	if (Arrays.equals(srcMac, zeroMac)) {
		log.warn("Port {}/{} has zero hardware address"
		+ "overwrite with lower 6 bytes of dpid",
		dpid.toString(), ofpPort.getPortNo().getPortNumber());
		System.arraycopy(dpidArray, 2, srcMac, 0, 6);
	}

	// set the portId to the outgoing port

	portBB.putShort(port.getShortPortNumber());
	LLDP lldp = new LLDP();
	lldp.setChassisId(new LLDPTLV().setType((byte) 1)
	.setLength((short) chassisId.length)
	.setValue(chassisId));
	lldp.setPortId(new LLDPTLV().setType((byte) 2)
	.setLength((short) portId.length)
	.setValue(portId));
	lldp.setTtl(new LLDPTLV().setType((byte) 3)
	.setLength((short) ttlValue.length)
	.setValue(ttlValue));
	lldp.getOptionalTLVList().add(dpidTLV);

	// Add the controller identifier to the TLV value.
	lldp.getOptionalTLVList().add(controllerTLV);
	if (isReverse) {
	lldp.getOptionalTLVList().add(reverseTLV);
	} else {
	lldp.getOptionalTLVList().add(forwardTLV);
	}

	/*
	* Introduce a new TLV for med-granularity link latency detection.
	* If same controller, can assume system clock is the same, but
	* cannot guarantee processing time or account for network congestion.
	*
	* Need to include our OpenFlow OUI - 00-26-E1-01 (note 01; 00 is DPID);
	* save last 8 bytes for long (time in ms).
	*
	* Note Long.SIZE is in bits (64).
	*/

	long time = System.nanoTime() / 1000000;
	long swLatency = iofSwitch.getLatency().getValue();
	if (log.isTraceEnabled()) {
		log.trace("SETTING LLDP LATENCY TLV: Current Time {}; {} control plane latency {}; sum {}", new Object[] { time, iofSwitch.getId(), swLatency, time + swLatency });
	}

	byte[] timestampTLVValue = ByteBuffer.allocate(Long.SIZE / 8 + 4)
	.put((byte) 0x00)
	.put((byte) 0x26)
	.put((byte) 0xe1)
	.put((byte) 0x01) /* 0x01 is what we'll use to differentiate DPID (0x00) from time (0x01) */
	.putLong(time + swLatency /* account for our switch's one-way latency */)
	.array();

	LLDPTLV timestampTLV = new LLDPTLV()
	.setType((byte) 127)
	.setLength((short) timestampTLVValue.length)
	.setValue(timestampTLVValue);

	/* Now add TLV to our LLDP packet */
	lldp.getOptionalTLVList().add(timestampTLV);
	Ethernet ethernet;
	if (isStandard) {
		ethernet = new Ethernet().setSourceMACAddress(ofpPort.getHwAddr())
		.setDestinationMACAddress(mac)   //Set trường này thành mac do chúng ta random
		.setEtherType(EthType.LLDP);
		ethernet.setPayload(lldp);
	} else {
		BSN bsn = new BSN(BSN.BSN_TYPE_BDDP);
		bsn.setPayload(lldp);
		ethernet = new Ethernet().setSourceMACAddress(ofpPort.getHwAddr())
		.setDestinationMACAddress(LLDP_BSN_DST_MAC_STRING)
		.setEtherType(EthType.of(Ethernet.TYPE_BSN & 0xffff)); /* treat as unsigned */
		ethernet.setPayload(bsn);
	}
	// serialize and wrap in a packet out

	byte[] data = ethernet.serialize();

	OFPacketOut.Builder pob = iofSwitch.getOFFactory().buildPacketOut()
	.setBufferId(OFBufferId.NO_BUFFER)
	.setActions(getDiscoveryActions(iofSwitch, port))
	.setData(data);
	OFMessageUtils.setInPort(pob, OFPort.CONTROLLER);
	log.debug("{}", pob.build());
	return pob.build();
}
````
* Sửa lại hàm sendDiscoveryMessage (bằng cách tạo mới hàm sendDiscoveryMessageTilak)
````java
public boolean sendDiscoveryMessageTilak(DatapathId sw, OFPort port, MacAddress mac,
boolean isStandard, boolean isReverse) {
	// Takes care of all checks including null pointer checks.
	if (!isOutgoingDiscoveryAllowed(sw, port, isStandard, isReverse)) {
		return false;
	}
	IOFSwitch iofSwitch = switchService.getSwitch(sw);
	if (iofSwitch == null) { // fix dereference violations in case race conditions
		return false;
	}
	counterPacketOut.increment();
	return iofSwitch.write(generateLLDPMessageTilak(iofSwitch, port, mac, isStandard, isReverse));
}
````
* Sửa lại hàm discoverOnAllPorts (bằng cách tạo mới hàm discoverOnFilterPorts)
````java
protected void discoverOnFilterPorts() {
		MacAddress rmac = MacAddress.of(LinkDiscoveryManager.randomMac());
		//Lưu địa chỉ MAC vừa random vào biến lmac để các class khác có thể truy cập giá trị này
		lmac = rmac;
		//log.info("Sending LLDP packets out of all the filter ports and random MAC {}",rmac.toString());
		// Send standard LLDPs
		log.info("Block all unkown LLDP packets on all available switches");
		log.info("Allow only LLDP packets with desination MAC of {} to be fowarded back to controller",rmac);
		for (DatapathId sw : switchService.getAllSwitchDpids()) {
			IOFSwitch iofSwitch = switchService.getSwitch(sw);
			if (iofSwitch == null) continue;
			if (!iofSwitch.isActive()) continue; /* can't do anything if the switch is SLAVE */
			this.writeBlock(iofSwitch);	
			this.writeFlowMod(iofSwitch, rmac);
		}
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
* Sửa lại command handleLLDP, bằng cách thêm đoạn code sau
````java
OFPort remotePort = OFPort.of(portBB.getShort());
		IOFSwitch remoteSwitch = null;
		long timestamp = 0;
		
		//code add them
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
			//System.out.println(iterator.next());
			String temp = iterator.next().toString();
			listtemp.add(temp);	    					
			for (String elements : listtemp)
				if (!listSwPort.contains(elements))
				{
					listSwPort.add(elements);
				}	    					
		}
		
		
		
		// Verify this LLDP packet matches what we're looking for
		for (LLDPTLV lldptlv : lldp.getOptionalTLVList()) {
			if (lldptlv.getType() == 127 && lldptlv.getLength() == 12
					&& lldptlv.getValue()[0] == 0x0
					....
````
* Sửa lại hàm discoverLink để chỉ khám phá trên các port đã filter trong bảng EligiblePort Table
````java
protected void discoverLinks() {

		// timeout known links.
		timeoutLinks();

		// increment LLDP clock
		lldpClock = (lldpClock + 1) % LLDP_TO_ALL_INTERVAL;

		if (lldpClock == 0) {
			if (log.isTraceEnabled())
				log.trace("Sending LLDP out on all ports.");
			//discoverOnAllPorts();
			discoverOnFilterPorts(); //Thay thế hàm thành hàm đã viết để quá trình
			//tìm link chỉ gửi LLDP trên port đã filter
		}
	}
````
* Log lại thiết bị đang cố gắng tấn công LLDP bằng cách sửa hàm handleMessage trong class OFSwitchManager. Khi có gói tin LLDP từ host gửi đến switch khác địa chỉ MAC random tại chu kì LLDP hiện tại, sẽ log ra thông báo phát hiện tấn công.
````java
@Override
protected void handleMessage(IOFSwitchBackend sw, OFMessage m, FloodlightContext bContext) {
	floodlightProvider.handleMessage(sw, m, bContext);
	Ethernet eth = null;
	if (m.getType().toString() == "PACKET_IN") {
		OFPacketIn pi = (OFPacketIn)m;
		if (pi.getData().length <= 0) {
			log.error("Ignore packet because it is empty");
			return;
		} else {
			eth = new Ethernet();
			eth.deserialize(pi.getData(), 0, pi.getData().length);
			if (eth.getPayload() instanceof LLDP) {
				MacAddress dstmac = eth.getDestinationMACAddress();
				if (!dstmac.equals(LinkDiscoveryManager.getCurrentMac())) {
					DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        	        	Date date = new Date();
        	        	log.warn("Detect LLDP attack at switch {} on device with port {}",sw.getId().toString(),pi.getMatch().toString());
        	        	String d = df.format(date).toString();
        	        	String t = "[" + d + "]: " + "Detect LLDP attack at switch " + sw.getId().toString() + " on device with port " + pi.getMatch().toString();
        	        	try {File file = new File("attack.log");
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
