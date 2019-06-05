# RAN BIEN
## Problem 
Chong tan cong LLDP
## Solution
Sua lai qua trinh lan truyen LLDP nhu sau:
* Controller random mot dia chi MAC: **rmac**
* Add flow vao tat ca cac switch cho phep goi tin LLDP co destination MAC la **rmac** di ve controller
* Controll generate cac goi LLDP co field **dstMAC = rmac**
  * O thoi diem t0, gui den tat ca cac port cua tat ca cac switch
  * Dua vao goi LLDP tra ve, controller loai bo cac port cua cac switch theo tieu chi: port khong gui LLDP den switch khac.
## Viec da lam
* Gan nhu toan bo viec lan truyen LLDP, cap nhat link giua cac switch deu duoc mo ta trong class LinkDiscoveryManager, nen ta se sua luon trong file nay
* Mac dinh, truong dstMac trong goi LLDP do controller gui den cac switch se co gia tri **"01:80:c2:00:00:0e"**,  em sua lai nhu sau:
````java
@Override
public OFPacketOut generateLLDPMessage(IOFSwitch iofSwitch, OFPort port, 
			boolean isStandard, boolean isReverse) {
	....
	//LLDP and BDDP fields
	//Comment dong nay lai
	//private static final byte[] LLDP_STANDARD_DST_MAC_STRING =
			//MacAddress.of("01:80:c2:00:00:0e").getBytes();
	//Viet them ham randomMac
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
	//Sua lai ham generate ra goi LLDP
	...
	Ethernet ethernet;
		if (isStandard) {
			ethernet = new Ethernet().setSourceMACAddress(ofpPort.getHwAddr())
					.setDestinationMACAddress(this.randomMac())
					.setEtherType(EthType.LLDP);
			ethernet.setPayload(lldp);
	...
````
* Toi buoc nay, em dung wireshak de bat goi tin thi no van dang chay dung voi expect
* Tiep theo, em muon khi no gui LLDP den cac switch thi no dong thoi add flow: cho phep goi tin LLDP co chua dstMac random cua minh di ve controller.
````java
public class LinkDiscoveryManager implements IOFMessageListener,
IOFSwitchListener, IStorageSourceListener, ILinkDiscoveryService,
IFloodlightModule, IInfoProvider {
	...	
	//FlowModClass elements
	protected U64 cookie;
	public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 15; 	
	public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 15; 	
	public static int FLOWMOD_DEFAULT_PRIORITY = 1;
	public static int FLOWMOD_SAMPLE_APP_ID = 1024;		// APP_ID
	...
	//Add flow function
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
		fmb.setCookie( cookie )		
		.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
		.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
		.setBufferId(OFBufferId.NO_BUFFER)
		.setMatch( mb.build() )
		.setActions( al )
		.setPriority(FLOWMOD_DEFAULT_PRIORITY);
		
		// finally write it out to switch
		sw.write( fmb.build() );
		//sw.flush();
		
	}
	...
	//Sua ham gui LLDP den cac switch, de no add flow 
	/**
	 * Send LLDPs to all switch-ports
	 */
	protected void discoverOnAllPorts() {
		log.info("Sending LLDP packets out of all the enabled ports");
		// Send standard LLDPs
		for (DatapathId sw : switchService.getAllSwitchDpids()) {
			IOFSwitch iofSwitch = switchService.getSwitch(sw);
			if (iofSwitch == null) continue;
			if (!iofSwitch.isActive()) continue; /* can't do anything if the switch is SLAVE */
			this.writeFlowMod(iofSwitch, MacAddress.of(this.randomMac())); //Em them dong nay vo ne
			....
````
* Khi run, goi LLDP dau tien phat ra no throw exception bao error trong ham Time gi do, anh giup em debug cho nay :(((
````
2019-06-04 23:18:14.900 INFO  [n.f.l.i.LinkDiscoveryManager] Sending LLDP packets out of all the enabled ports
2019-06-04 23:18:15.519 ERROR [n.f.l.i.LinkDiscoveryManager] Exception in LLDP send timer.
java.lang.NullPointerException: Property cookie must not be null
	at org.projectfloodlight.openflow.protocol.ver14.OFFlowAddVer14$Builder.build(OFFlowAddVer14.java:748) ~[openflowj-3.3.0-SNAPSHOT.jar:na]
	at org.projectfloodlight.openflow.protocol.ver14.OFFlowAddVer14$Builder.build(OFFlowAddVer14.java:518) ~[openflowj-3.3.0-SNAPSHOT.jar:na]
	at net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager.writeFlowMod(LinkDiscoveryManager.java:317) ~[bin/:na]
	at net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager.discoverOnAllPorts(LinkDiscoveryManager.java:1274) ~[bin/:na]
	at net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager.discoverLinks(LinkDiscoveryManager.java:993) ~[bin/:na]
	at net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager$1.run(LinkDiscoveryManager.java:2122) ~[bin/:na]
	at net.floodlightcontroller.core.util.SingletonTask$SingletonTaskWorker.run(SingletonTask.java:69) [bin/:na]
	at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511) [na:1.8.0_201]
	at java.util.concurrent.FutureTask.run(FutureTask.java:266) [na:1.8.0_201]
	at java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.access$201(ScheduledThreadPoolExecutor.java:180) [na:1.8.0_201]
	at java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:293) [na:1.8.0_201]
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149) [na:1.8.0_201]
````

