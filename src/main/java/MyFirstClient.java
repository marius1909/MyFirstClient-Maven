import com.novi.serde.Bytes;
import example.GetEventsExample;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import com.diem.*;
import com.diem.jsonrpc.StaleResponseException;
import com.diem.jsonrpc.JsonRpc;
import com.diem.jsonrpc.JsonRpc.Account;
import com.diem.stdlib.Helpers;
import com.diem.types.RawTransaction;
import com.diem.types.SignedTransaction;
import com.diem.types.TransactionPayload;
import com.diem.utils.CurrencyCode;

import java.security.SecureRandom;

import static com.diem.AccountIdentifier.NetworkPrefix.TestnetPrefix;
import static com.diem.IntentIdentifier.decode;
import static com.diem.Testnet.CHAIN_ID;

public class MyFirstClient {
    //the default currency on testnet
    public static final String CURRENCY_CODE = "XUS";

    /**
     *
     * This code demonstrates basic flow for working with the DiemClient.
     * 1. Connect to testnet
     * 2. Generate keys
     * 3. Create account - by minting
     * 4. Get account information
     * 5. Start events listener
     * 6. Add money to existing account (mint)
     * 7. Generate keys for second account
     * 8. Create second account (for the benefit of the following transaction)
     * 9. Generate IntentIdentifier
     * 10. Deserialize IntentIdentifier
     * 11. Transfer money between accounts (peer to peer transaction)
     *
     */
    public static void main(String[] args) throws DiemException {
        System.out.println("");
        System.out.println("#1 Connect to testnet");
        DiemClient client = Testnet.createClient();

        System.out.println("#2 Generate Keys");
        PrivateKey senderPrivateKey = new Ed25519PrivateKey(new Ed25519PrivateKeyParameters(new SecureRandom()));
        AuthKey senderAuthKey = AuthKey.ed25519(senderPrivateKey.publicKey());

        System.out.println("#3 Create wallet");
        Testnet.mintCoins(client, 100000000, senderAuthKey.hex(), CURRENCY_CODE);

        System.out.println("#4 Get account information");
        Account senderAccount = client.getAccount(senderAuthKey.accountAddress());

        String eventsKey = senderAccount.getReceivedEventsKey();
        System.out.println("#5 Subscribe to events stream");
        GetEventsExample.subscribe(client, eventsKey);

        System.out.println("#6 Add money to account");
        Testnet.mintCoins(client, 10000000, senderAuthKey.hex(), CURRENCY_CODE);

        System.out.println("#7 Generate Keys for second wallet");
        //generate private key for new account
        PrivateKey receiverPrivateKey = new Ed25519PrivateKey(new Ed25519PrivateKeyParameters(new SecureRandom()));
        //generate auth key for new account
        AuthKey receiverAuthKey = AuthKey.ed25519(receiverPrivateKey.publicKey());

        System.out.println("#8 Add funds from mint to the second wallet" + receiverAuthKey.hex());
        Testnet.mintCoins(client, 1000000L, receiverAuthKey.hex(), CURRENCY_CODE);

        System.out.println("#9 Generate IntentIdentifier");
        AccountIdentifier accountIdentifier = new AccountIdentifier(TestnetPrefix, receiverAuthKey.accountAddress());
        IntentIdentifier intentIdentifier = new IntentIdentifier(accountIdentifier, CURRENCY_CODE, 100000000L);
        String intentIdentifierString = intentIdentifier.encode();

        System.out.println("Encoded IntentIdentifier: " + intentIdentifierString);

        System.out.println("#10 Deserialize IntentIdentifier");
        IntentIdentifier decodedIntentIdentifier = decode(TestnetPrefix, intentIdentifierString);

        System.out.println("#11 Peer-to-peer transaction");
        //Create script
        TransactionPayload script = new TransactionPayload.Script(
                Helpers.encode_peer_to_peer_with_metadata_script(
                        CurrencyCode.typeTag(decodedIntentIdentifier.getCurrency()),
                        decodedIntentIdentifier.getAccountIdentifier().getAccountAddress(),
                        decodedIntentIdentifier.getAmount(),
                        new Bytes(new byte[0]),
                        new Bytes(new byte[0])));
        //Create transaction
        RawTransaction rawTransaction = new RawTransaction(
                senderAuthKey.accountAddress(),
                senderAccount.getSequenceNumber(),
                script,
                1000000L,
                0L,
                CURRENCY_CODE,
                (System.currentTimeMillis() / 1000) + 300,
                CHAIN_ID);
        //Sign transaction
        SignedTransaction st = Signer.sign(senderPrivateKey, rawTransaction);
        //Submit transaction
        try {
            client.submit(st);
        } catch (StaleResponseException e) {
            //ignore
        }
        //Wait for the transaction to complete new SecureRandom()
        try {
            JsonRpc.Transaction transaction = client.waitForTransaction(st, 100000);

            System.out.println(transaction);
        } catch (DiemException e) {
            throw new RuntimeException("Failed while waiting for transaction ", e);
        }
    }
}



