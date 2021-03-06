package Agent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import Agent.Crop.cropType;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.text.DecimalFormat;

public class randValSealbidedBidder extends Agent {
    randValue randValue = new randValue();
    DecimalFormat df = new DecimalFormat("#.##");

    agentInfo bidderInfo = new agentInfo("","bidder", randValue.getRandDoubleRange(13,15), randValue.getRandDoubleRange(300,1000),0.0, 0.0, 0);

    protected void setup(){
        System.out.println(getAID().getLocalName() + "is Ready");
        bidderInfo.farmerName = getAID().getLocalName();
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("bidder");
        sd.setName(getAID().getName());
        dfd.addServices(sd);
        try{
            DFService.register(this, dfd);
        }
        catch (FIPAException fe){
            fe.printStackTrace();
        }

        //Bidding process
        addBehaviour(new TickerBehaviour(this, 1000) {
            protected void onTick() {
                System.out.println("Agent Name: " + bidderInfo.farmerName + "  " + "Buying price: " + bidderInfo.buyingPrice + "  " + "Water volumn need: " + bidderInfo.buyingVolumn);
                addBehaviour(new OfferRequestsServer());
                addBehaviour(new PurchaseOrdersServer());
            }
        });
    }

    protected void takeDown(){

    }

    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("Seller");
            template.addServices(sd);
            try{
                DFAgentDescription[] result = DFService.search(myAgent, template);
                bidderInfo.numSeller = result.length;
            }catch (FIPAException fe){
                fe.printStackTrace();
            }

            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            String log = new String();
            //CFP Message received. Process it.
            if(msg != null){
                ACLMessage reply = msg.createReply();
                System.out.println("True");

                //Current price Per MM. and the number of volumn to sell.
                String currentOffer = msg.getContent();
                String[] arrOfstr = currentOffer.split("-");
                bidderInfo.offeredVolumn = Double.parseDouble(arrOfstr[0]);
                bidderInfo.offeredPrice = Double.parseDouble(arrOfstr[1]);

                //Sealed bid acution;
                if (bidderInfo.buyingPrice < bidderInfo.offeredPrice && bidderInfo.buyingVolumn < bidderInfo.offeredVolumn) {
                    if(bidderInfo.numSeller > 0){
                        bidderInfo.numSeller = bidderInfo.numSeller -1;
                    }

                    String currentBidOffer;
                    currentBidOffer = bidderInfo.buyingVolumn + "-" + bidderInfo.buyingPrice;
                    reply.setContent(currentBidOffer);
                    myAgent.send(reply);
                }else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    //reply.setContent(getAID().getName() + " is surrender");
                    myAgent.send(reply);
                    //myGUI.displayUI(getAID().getName() + " is surrender");
                }
            }else {
                block();
            }
        }
    }

    private class PurchaseOrdersServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                //myGUI.displayUI("Accept Proposal Message: " + msg.toString() +"\n");
                // ACCEPT_PROPOSAL Message received. Process it
                //Double volumnTemp = Double.parseDouble(msg.getContent());
                bidderInfo.buyingVolumn = 0.0;
                ACLMessage reply = msg.createReply();
                //reply.setContent(String.valueOf(volumnTemp));
                //System.out.println(farmerInfo.sellingStatus);
                reply.setPerformative(ACLMessage.INFORM);
                myAgent.send(reply);
                //water requirement for next round bidding.
                //myGUI.displayUI(msg.getSender().getLocalName()+" sell water to "+ getAID().getLocalName() +"\n");
                if (bidderInfo.buyingVolumn <=0) {
                    bidderInfo.sellingStatus = "Finished bidding";
                    //myGUI.displayUI(getAID().getLocalName() +  "is complete in buying process" + "\n" + getAID().getLocalName() + "terminating");
                    myAgent.doDelete();
                    //myAgent.doSuspend();
                    //myGUI.dispose();
                    System.out.println(getAID().getName() + " terminating.");
                }
            }else {
                block();
            }
        }
    }

    public class agentInfo{
        String farmerName;
        String sellingStatus;
        Double buyingPrice;
        Double buyingVolumn;
        Double offeredPrice;
        Double offeredVolumn;
        int numSeller;

        agentInfo(String farmerName, String agentType, double buyingPrice, double buyingVolumn, double offeredPrice, double offeredVolumn, int numSeller){
            this.farmerName = farmerName;
            this.sellingStatus = agentType;
            this.buyingPrice = buyingPrice;
            this.buyingVolumn = buyingVolumn;
            this.offeredPrice = offeredPrice;
            this.offeredVolumn = offeredVolumn;
            this.numSeller = numSeller;
        }
    }
}