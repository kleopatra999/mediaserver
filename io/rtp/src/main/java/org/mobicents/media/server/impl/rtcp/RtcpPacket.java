/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.media.server.impl.rtcp;

import java.io.Serializable;

import org.apache.log4j.Logger;

/**
 * 
 * @author Amit Bhayani
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 */
public class RtcpPacket implements Serializable {
	
	private static final long serialVersionUID = -7175947723683038337L;

	private static final Logger logger = Logger.getLogger(RtcpPacket.class);

	/**
	 * Maximum number of reporting sources
	 */
	public static final int MAX_SOURCES = 31;
	
	private RtcpSenderReport senderReport = null;
	private RtcpReceiverReport receiverReport = null;
	private RtcpSdes sded = null;
	private RtcpBye bye = null;
	private RtcpAppDefined appDefined = null;
	
	private int packetCount = 0;
	private int size = 0;
	
	public RtcpPacket() {

	}

	public RtcpPacket(RtcpSenderReport senderReport, RtcpReceiverReport receiverReport, RtcpSdes sdes, RtcpBye bye, RtcpAppDefined appDefined) {
		this.senderReport = senderReport;
		this.receiverReport = receiverReport;
		this.sded = sdes;
		this.bye = bye;
		this.appDefined = appDefined;
	}

	public int decode(byte[] rawData, int offSet) {
		this.size = rawData.length - offSet;
		while (offSet < rawData.length) {
			int type = rawData[offSet + 1] & 0x000000FF;
			switch (type) {
			case RtcpHeader.RTCP_SR:
				packetCount++;
				this.senderReport = new RtcpSenderReport();
				offSet = this.senderReport.decode(rawData, offSet);
				break;
			case RtcpHeader.RTCP_RR:
				packetCount++;
				this.receiverReport = new RtcpReceiverReport();
				offSet = this.receiverReport.decode(rawData, offSet);
				break;
			case RtcpHeader.RTCP_SDES:
				packetCount++;
				this.sded = new RtcpSdes();
				offSet = this.sded.decode(rawData, offSet);
				break;
			case RtcpHeader.RTCP_APP:
				packetCount++;
				this.appDefined = new RtcpAppDefined();
				offSet = this.appDefined.decode(rawData, offSet);
				break;
			case RtcpHeader.RTCP_BYE:
				packetCount++;
				this.bye = new RtcpBye();
				offSet = this.bye.decode(rawData, offSet);
				break;
			default:				
				logger.error("Received type = "+type+" RTCP Packet decoding falsed. offSet = "+offSet);
				offSet = rawData.length;
				break;
			}
		}

		return offSet;
	}

	public int encode(byte[] rawData, int offSet) {
		if (this.senderReport != null) {
			packetCount++;
			offSet = this.senderReport.encode(rawData, offSet);
		}
		if (this.receiverReport != null) {
			packetCount++;
			offSet = this.receiverReport.encode(rawData, offSet);
		}
		if (this.sded != null) {
			packetCount++;
			offSet = this.sded.encode(rawData, offSet);
		}
		if (this.appDefined != null) {
			packetCount++;
			offSet = this.appDefined.encode(rawData, offSet);
		}
		if (this.bye != null) {
			packetCount++;
			offSet = this.bye.encode(rawData, offSet);
		}
		return offSet;
	}
	
	public boolean isSender() {
		return this.senderReport != null;
	}
	
	public RtcpPacketType getPacketType() {
		if(this.bye == null) {
			return RtcpPacketType.RTCP_REPORT;
		}
		return RtcpPacketType.RTCP_REPORT;
	}
	
	public RtcpReport getReport() {
		if(isSender()) {
			return this.senderReport;
		}
		return this.receiverReport;
	}

	public RtcpSenderReport getSenderReport() {
		return senderReport;
	}

	public RtcpReceiverReport getReceiverReport() {
		return receiverReport;
	}

	public RtcpSdes getSdes() {
		return sded;
	}

	public RtcpBye getBye() {
		return bye;
	}

	public RtcpAppDefined getAppDefined() {
		return appDefined;
	}

	public int getPacketCount() {
		return packetCount;
	}

	public int getSize() {
		return size;
	}
}
