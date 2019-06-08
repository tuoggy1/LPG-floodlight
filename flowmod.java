Class LinkDiscoveryManager ....
{
		public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5; 	// infinite
		public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 5; 	// infinite
		public static int FLOWMOD_DEFAULT_PRIORITY = 1
		
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
			.setPriority(FLOWMOD_DEFAULT_PRIORITY);
			
			// finally write it out to switch
			sw.write( fmb.build() );
			//sw.flush();
			
			logger.debug("Sending 1 new entry to {}", sw.getId().toString() );
		}
}
protected void discoverOnAllPorts() {	
		....................						
		for (DatapathId sw : switchService.getAllSwitchDpids()) {
			IOFSwitch iofSwitch = switchService.getSwitch(sw);
			if (iofSwitch == null) continue;
			if (!iofSwitch.isActive()) continue; /* can't do anything if the switch is SLAVE */
			this.writeFlowMod(iofSwitch, rmac);
			Collection<OFPort> c = iofSwitch.getEnabledPortNumbers();
