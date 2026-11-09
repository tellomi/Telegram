package org.telegram.tgnet.tl;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.Vector;

import java.util.ArrayList;

/**
 * Tellomi discover constructors. They are not in the official layer schema.
 * Ids match teamgram schema.tl.discover.proto at e2868f53.
 */
public class TL_discover {

    public static class TL_discover_engineCredential extends TLObject {
        public static final int constructor = 0x0578ccaa;

        public String token;
        public int expires;
        public String region;

        public static TL_discover_engineCredential TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_discover_engineCredential.constructor != constructor) {
                return null;
            }
            TL_discover_engineCredential result = new TL_discover_engineCredential();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            token = stream.readString(exception);
            expires = stream.readInt32(exception);
            region = stream.readString(exception);
        }
    }

    public static class TL_discover_getEngineCredential extends TLObject {
        public static final int constructor = 0xa80a7f76;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_discover_engineCredential.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_discover_audienceToken extends TLObject {
        public static final int constructor = 0xda177181;

        public String token;
        public int expires;

        public static TL_discover_audienceToken TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_discover_audienceToken.constructor != constructor) {
                return null;
            }
            TL_discover_audienceToken result = new TL_discover_audienceToken();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            token = stream.readString(exception);
            expires = stream.readInt32(exception);
        }
    }

    public static class TL_discover_getAudienceToken extends TLObject {
        public static final int constructor = 0xdc5213c2;

        public String kind;
        public ArrayList<Long> user_ids = new ArrayList<>();

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_discover_audienceToken.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(kind);
            stream.writeInt32(Vector.constructor);
            stream.writeInt32(user_ids.size());
            for (int i = 0; i < user_ids.size(); i++) {
                stream.writeInt64(user_ids.get(i));
            }
        }
    }
}
