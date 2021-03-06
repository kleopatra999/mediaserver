/*
 * JBoss, Home of Professional Open Source
 * Copyright XXXX, Red Hat Middleware LLC, and individual contributors as indicated
 * by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a full listing
 * of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package org.mobicents.javax.media.mscontrol.mediagroup;

import java.util.HashSet;
import java.util.Set;
import javax.media.mscontrol.Configuration;
import javax.media.mscontrol.EventType;
import javax.media.mscontrol.MediaConfig;
import javax.media.mscontrol.MediaObject;
import javax.media.mscontrol.Parameter;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.Qualifier;
import javax.media.mscontrol.Value;
import javax.media.mscontrol.mediagroup.signals.SignalDetector;
import javax.media.mscontrol.mediagroup.signals.SignalDetectorEvent;
import javax.media.mscontrol.resource.Action;
import javax.media.mscontrol.resource.Trigger;
import org.mobicents.javax.media.mscontrol.MediaConfigImpl;
import org.mobicents.javax.media.mscontrol.MediaObjectImpl;
import org.mobicents.javax.media.mscontrol.ParametersImpl;
import org.mobicents.javax.media.mscontrol.SupportedFeaturesImpl;

/**
 *
 * @author kulikov
 */
public class SignalDetectorConfig implements Configuration {
    
    private MediaConfigImpl config;
    private Parameters params = new ParametersImpl();
    
    protected SignalDetectorConfig() {
        
        //specify parameters
        Set<Parameter> parameters = new HashSet();
        parameters.add(SignalDetector.INITIAL_TIMEOUT);
        parameters.add(SignalDetector.MAX_DURATION);
        parameters.add(SignalDetector.INTER_SIG_TIMEOUT);
        for (Parameter p : SignalDetector.PATTERN) {
            parameters.add(p);
        }
        
        //specify event types
        Set<EventType> eventTypes = new HashSet();
        eventTypes.add(SignalDetectorEvent.OVERFLOWED);
        eventTypes.add(SignalDetectorEvent.RECEIVE_SIGNALS_COMPLETED);
        eventTypes.add(SignalDetectorEvent.SIGNAL_DETECTED);
        for (EventType e : SignalDetectorEvent.PATTERN_MATCHED) {
            eventTypes.add(e);
        }
        
        //Define actions
        Set<Action> actions = new HashSet();
        
        //Define qualifiers
        Set<Qualifier> qualifiers = new HashSet();
        qualifiers.add(SignalDetectorEvent.DURATION_EXCEEDED);
        qualifiers.add(SignalDetectorEvent.INITIAL_TIMEOUT_EXCEEDED);
        qualifiers.add(SignalDetectorEvent.INTER_SIG_TIMEOUT_EXCEEDED);
        qualifiers.add(SignalDetectorEvent.NUM_SIGNALS_DETECTED);
        for (Qualifier q : SignalDetectorEvent.PATTERN_MATCHING) {
            qualifiers.add(q);
        }
        qualifiers.add(SignalDetectorEvent.PROMPT_FAILURE);
        
        //Define triggers
        Set<Trigger> triggers = new HashSet();
        triggers.add(SignalDetector.DETECTION_OF_ONE_SIGNAL);
        triggers.add(SignalDetector.FLUSHING_OF_BUFFER);
        for (Trigger t : SignalDetector.PATTERN_MATCH) {
            triggers.add(t);
        }
        triggers.add(SignalDetector.RECEIVE_SIGNALS_COMPLETION);

        //Define values
        Set<Value> values = new HashSet();
        
        params.put(MediaObjectImpl.ENDPOINT_NAME, "mobicents/ivr/$");
        
        SupportedFeaturesImpl features = new SupportedFeaturesImpl(parameters, actions, eventTypes, qualifiers, triggers, values);
        config = new MediaConfigImpl(features, params);
    }
    
    public MediaConfig getConfig() {
        return config;
    }
}
