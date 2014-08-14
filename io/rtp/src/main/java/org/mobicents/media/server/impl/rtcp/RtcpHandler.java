package org.mobicents.media.server.impl.rtcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.mobicents.media.server.impl.rtp.RtpStatistics;
import org.mobicents.media.server.io.network.channel.PacketHandler;
import org.mobicents.media.server.io.network.channel.PacketHandlerException;

/**
 * 
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 * 
 */
public class RtcpHandler implements PacketHandler {

	private static final Logger logger = Logger.getLogger(RtcpHandler.class);
	
	/**
	 * Time (in ms) between SSRC Task executions
	 */
	private static final long SSRC_TASK_DELAY = 7000;

	/*
	 * Core elements
	 */
	/** Timer that sends RTCP packet on expiration */
	private final Timer txTimer;

	/** Timer that checks for SSRC timeouts on expiration */
	private final Timer ssrcTimer;
	
	private final SsrcTask ssrcTask;

	/** Datagram channel used to send the RTCP packets to the remote peer */
	private DatagramChannel channel;

	/** Buffer used to wrap RTCP packets */
	private ByteBuffer byteBuffer;

	/*
	 * RTCP elements
	 */
	/** Stores statistics regarding the RTP session */
	private final RtpStatistics statistics;

	/** The scheduled task responsible for transmitting the RTCP packet. */
	private TxTask scheduledTask;

	/** The last time an RTCP packet was transmitted */
	private long tp;

	/** The next scheduled transmission time of an RTCP packet */
	private long tn;

	/** Flag that is true if the application has not yet sent an RTCP packet */
	private boolean initial;

	public RtcpHandler(final RtpStatistics statistics) {
		// core stuff
		this.txTimer = new Timer();
		this.ssrcTimer = new Timer();
		this.ssrcTask = new SsrcTask();

		// rtcp stuff
		this.statistics = statistics;
		this.scheduledTask = null;
		this.tp = 0;
		this.initial = true;
	}

	/**
	 * Gets the current time. Useful for scheduling calculations.
	 * 
	 * @return The current time of the system in milliseconds
	 */
	private long getCurrentTime() {
		return System.currentTimeMillis();
	}

	/**
	 * Gets the timestamp of a future moment in time.
	 * 
	 * @param delay
	 *            The amount of time in the future, in milliseconds
	 * @return The timestamp of the date matching the delay
	 */
	private long resolveDelay(long delay) {
		return getCurrentTime() + delay;
	}

	/**
	 * Gets the time interval between the current time and another timestamp.
	 * 
	 * @param timestamp
	 *            The timestamp to compare to the current date
	 * @return The interval of time between both dates, in milliseconds.
	 */
	private long resolveInterval(long timestamp) {
		return timestamp - getCurrentTime();
	}

	/**
	 * Upon joining the session, the participant initializes tp to 0, tc to 0,
	 * senders to 0, pmembers to 1, members to 1, we_sent to false, rtcp_bw to
	 * the specified fraction of the session bandwidth, initial to true, and
	 * avg_rtcp_size to the probable size of the first RTCP packet that the
	 * application will later construct.
	 * 
	 * The calculated interval T is then computed, and the first packet is
	 * scheduled for time tn = T. This means that a transmission timer is set
	 * which expires at time T. Note that an application MAY use any desired
	 * approach for implementing this timer.
	 * 
	 * The participant adds its own SSRC to the member table.
	 */
	public void joinRtpSession() {
		// Schedule first RTCP packet
		long t = this.statistics.rtcpInterval(this.initial);
		this.tn = resolveDelay(t);
		RtcpPacket packet = new RtcpPacket();
		// TODO build an RR packet
		schedule(this.tn, packet);
		
		// Start SSRC timeout timer
		this.ssrcTimer.scheduleAtFixedRate(this.ssrcTask, SSRC_TASK_DELAY, SSRC_TASK_DELAY);
	}

	public void leaveRtpSession() {
		// Stop SSRC checks
		this.ssrcTimer.cancel();
		
		// Create a RTCP BYE packet to be scheduled
		// TODO Build RTCP BYE packet
		RtcpPacket bye = new RtcpPacket();

		/*
		 * When the participant decides to leave the system, tp is reset to tc,
		 * the current time, members and pmembers are initialized to 1, initial
		 * is set to 1, we_sent is set to false, senders is set to 0, and
		 * avg_rtcp_size is set to the size of the compound BYE packet.
		 * 
		 * The calculated interval T is computed. The BYE packet is then
		 * scheduled for time tn = tc + T.
		 */
		this.tp = System.currentTimeMillis();
		this.statistics.resetMembers();
		this.initial = true;
		this.weSent = false;
		this.statistics.clearSenders();
		this.statistics.setAvgRtcpSize(bye.getSize());

		long t = this.statistics.rtcpInterval(initial);
		this.tn = resolveDelay(t);
		schedule(this.tn, bye);
	}

	/**
	 * Schedules an event to occur at a certain time.
	 * 
	 * @param timestamp
	 *            The timestamp of the date when the event should be fired
	 * @param packet
	 *            The RTCP packet to be sent when the timer expires
	 */
	private void schedule(long timestamp, RtcpPacket packet) {
		this.scheduledTask = new TxTask(timestamp, packet);
		long interval = resolveInterval(timestamp);
		if (interval >= 0) {
			this.txTimer.schedule(this.scheduledTask, interval);
			// Let the RTP handler know what is the type of scheduled packet
			this.statistics.setScheduledPacketType(packet.getPacketType());
		} else {
			// TODO handle this scenario
		}
	}

	/**
	 * Re-schedules a previously scheduled event
	 * 
	 * @param timestamp
	 *            The new date of the event
	 */
	private void reschedule(TxTask task, long timestamp) {
		task.cancel();
		task.setTimestamp(timestamp);
		long interval = resolveInterval(timestamp);
		if (interval >= 0) {
			this.txTimer.schedule(task, timestamp);
		} else {
			// TODO handle this scenario
		}
	}

	/**
	 * This function is responsible for deciding whether to send an RTCP report
	 * or BYE packet now, or to reschedule transmission.
	 * 
	 * It is also responsible for updating the pmembers, initial, tp, and
	 * avg_rtcp_size state variables. This function should be called upon
	 * expiration of the event timer used by Schedule().
	 * 
	 * @param task
	 *            The scheduled task whose timer expired
	 * 
	 * @throws IOException
	 *             When a packet cannot be sent over the datagram channel
	 */
	private void onExpire(TxTask task) throws IOException {
		long tc = getCurrentTime();
		switch (task.getType()) {
		case RTCP_REPORT:
			long t = this.statistics.rtcpInterval(this.initial);
			this.tn = this.tp + t;

			if (this.tn <= tc) {
				RtcpPacket packet = task.getPacket();
				sendRtcpPacket(packet);

				int sentPacketSize = packet.getSize();
				this.statistics.calculateAgvRtcpSize(sentPacketSize);
				this.tp = tc;

				/*
				 * We must redraw the interval. Don't reuse the one computed
				 * above, since its not actually distributed the same, as we are
				 * conditioned on it being small enough to cause a packet to be
				 * sent.
				 */
				t = this.statistics.rtcpInterval(this.initial);

				RtcpPacket nextPacket = new RtcpPacket();
				// TODO Build RTCP_SR packet
				schedule(tc + t, nextPacket);
				initial = false;
			} else {
				RtcpPacket nextPacket = new RtcpPacket();
				// TODO Build RTCP_SR packet
				schedule(tn, nextPacket);
			}

			this.statistics.confirmMembers();
			break;

		case RTCP_BYE:
			/*
			 * In the case of a BYE, we use "timer reconsideration" to
			 * reschedule the transmission of the BYE if necessary
			 */
			t = this.statistics.rtcpInterval(this.initial);
			this.tn = this.tp + t;

			if (this.tn <= tc) {
				sendRtcpPacket(task.getPacket());
				return;
			} else {
				RtcpPacket nextPacket = new RtcpPacket();
				// TODO Build RTCP_BYE packet
				schedule(this.tn, nextPacket);
			}
			break;

		default:
			break;
		}

	}

	public boolean canHandle(byte[] packet) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean canHandle(byte[] packet, int dataLength, int offset) {
		// TODO Auto-generated method stub
		return false;
	}

	public byte[] handle(byte[] packet) throws PacketHandlerException {
		return handle(packet, packet.length, 0);
	}

	public byte[] handle(byte[] packet, int dataLength, int offset)
			throws PacketHandlerException {
		if (!canHandle(packet, dataLength, offset)) {
			logger.warn("Cannot handle incoming packet");
			throw new PacketHandlerException("Cannot handle incoming packet");
		}

		// Decode the RTCP compound packet
		RtcpPacket rtcpPacket = new RtcpPacket();
		rtcpPacket.decode(packet, offset);

		/*
		 * All RTCP packets MUST be sent in a compound packet of at least two
		 * individual packets. The first RTCP packet in the compound packet MUST
		 * always be a report packet to facilitate header validation
		 */
		RtcpReport report = rtcpPacket.getReport();
		long ssrc = report.getSsrc();

		/*
		 * What we do depends on whether we have left the group, and are waiting
		 * to send a BYE or an RTCP report.
		 */
		switch (rtcpPacket.getPacketType()) {
		case RTCP_REPORT:

			/*
			 * 6.3.3 - Receiving an RTP or Non-BYE RTCP Packet
			 * 
			 * When an RTP or (non-bye) RTCP packet is received from a
			 * participant whose SSRC is not in the member table, the SSRC is
			 * added to the table, and the value for members is updated once the
			 * participant has been validated.
			 */
			if (!this.statistics.isMember(ssrc)
					&& RtcpPacketType.RTCP_REPORT.equals(this.statistics
							.getScheduledPacketType())) {
				this.statistics.addMember(ssrc);
			}

			break;
		case RTCP_BYE:

			/*
			 * 6.3.4 - Receiving an RTCP BYE Packet
			 */
			switch (this.scheduledTask.getType()) {
			case RTCP_REPORT:

				/*
				 * If the received packet is an RTCP BYE packet, the SSRC is
				 * checked against the member table. If present, the entry is
				 * removed from the table, and the value for members is updated.
				 */
				if (this.statistics.isMember(ssrc)) {
					this.statistics.removeMember(ssrc);
				}

				/*
				 * The SSRC is then checked against the sender table. If
				 * present, the entry is removed from the table, and the value
				 * for senders is updated.
				 */
				if (this.statistics.isSender(ssrc)) {
					this.statistics.removeSender(ssrc);
				}

				/*
				 * To make the transmission rate of RTCP packets more adaptive
				 * to changes in group membership, the following "reverse
				 * reconsideration" algorithm SHOULD be executed when a BYE
				 * packet is received that reduces members to a value less than
				 * pmembers
				 */
				if (this.statistics.getMembers() < this.statistics.getPmembers()) {
					long tc = getCurrentTime();
					this.tn = tc + (this.statistics.getMembers() / this.statistics.getPmembers()) * (this.tn - tc);
					this.tp = tc - (this.statistics.getMembers() / this.statistics.getPmembers()) * (tc - this.tp);

					// Reschedule the next report for time tn
					reschedule(this.scheduledTask, this.tn);
					this.statistics.confirmMembers();
				}
				break;

			case RTCP_BYE:

				/*
				 * Every time a BYE packet from another participant is received,
				 * members is incremented by 1 regardless of whether that
				 * participant exists in the member table or not, and when SSRC
				 * sampling is in use, regardless of whether or not the BYE SSRC
				 * would be included in the sample.
				 * 
				 * members is NOT incremented when other RTCP packets or RTP
				 * packets are received, but only for BYE packets. Similarly,
				 * avg_rtcp_size is updated only for received BYE packets.
				 * senders is NOT updated when RTP packets arrive; it remains 0.
				 */
				this.statistics.addMember();
				break;

			default:
				
				// TODO print a warning
				break;
			}
			break;
		default:
			logger.warn("Unkown RTCP packet type. Dropping packet.");
			break;
		}

		/*
		 * For each compound RTCP packet received, the value of avg_rtcp_size is
		 * updated.
		 */
		this.statistics.calculateAgvRtcpSize(dataLength);

		// RTCP does not send replies
		return null;
	}

	private void sendRtcpPacket(RtcpPacket packet) throws IOException {
		if (this.channel != null && channel.isOpen()) {
			// decode packet
			byte[] data = new byte[packet.getSize()];
			int offset = packet.decode(data, 0);

			// prepare buffer
			this.byteBuffer.clear();
			this.byteBuffer.put(data, offset, data.length);
			this.byteBuffer.flip();

			// send packet
			this.channel.send(this.byteBuffer, this.channel.getRemoteAddress());
		} else {
			logger.warn("Could not send RTCP packet because channel is closed.");
			// TODO handle channel closed
		}
	}

	/**
	 * Schedulable task responsible for sending RTCP packets.
	 * 
	 * @author Henrique Rosa (henrique.rosa@telestax.com)
	 * 
	 */
	private class TxTask extends TimerTask {

		private long timestamp;
		private final RtcpPacket packet;

		public TxTask(long timestamp, RtcpPacket packet) {
			this.timestamp = timestamp;
			this.packet = packet;
		}

		public long getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(long timestamp) {
			this.timestamp = timestamp;
		}

		public RtcpPacket getPacket() {
			return this.packet;
		}

		public RtcpPacketType getType() {
			return this.packet.getPacketType();
		}

		@Override
		public void run() {
			try {
				onExpire(this);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	/**
	 * Schedulable task responsibable for cheking timeouts of registered SSRC.
	 * 
	 * @author Henrique Rosa
	 * 
	 */
	private class SsrcTask extends TimerTask {

		@Override
		public void run() {
			statistics.isSenderTimeout();
		}

	}

}
