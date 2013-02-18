package org.apache.stanbol.example;

import java.util.Date;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.apache.clerezza.rdf.core.BNode;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.NoSuchEntityException;
import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.ontologies.DC;
import org.apache.clerezza.rdf.ontologies.RDF;
import org.apache.clerezza.rdf.ontologies.RDFS;
import org.apache.clerezza.rdf.utils.GraphNode;
import org.apache.clerezza.rdf.utils.UnionMGraph;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.indexedgraph.IndexedMGraph;
import org.apache.stanbol.commons.web.viewable.RdfViewable;
import org.apache.stanbol.entityhub.core.impl.SiteManagerImpl;
import org.apache.stanbol.entityhub.core.query.FieldQueryImpl;
import org.apache.stanbol.entityhub.model.clerezza.RdfRepresentation;
import org.apache.stanbol.entityhub.model.clerezza.RdfValueFactory;
import org.apache.stanbol.entityhub.servicesapi.Entityhub;
import org.apache.stanbol.entityhub.servicesapi.model.Entity;
import org.apache.stanbol.entityhub.servicesapi.model.Representation;
import org.apache.stanbol.entityhub.servicesapi.query.FieldQuery;
import org.apache.stanbol.entityhub.servicesapi.query.QueryResultList;
import org.apache.stanbol.entityhub.servicesapi.site.SiteManager;

/**
 * Uses the DbPedia referenced site to resolve entities.
 */
@Component
@Service(Object.class)
@Property(name="javax.ws.rs", boolValue=true)
@Path("example-service")
public class ResourceResolver {
    
    /**
     * This service allows to get entities from configures sites
     */
    @Reference
    private SiteManager siteManager;
    
    /**
     * This service allows accessing and creating persistent triple collections
     */
    @Reference
    private TcManager tcManager;
    
    /**
     * This is the name of the graph in which we "log" the requests
     */
    private UriRef LOG_GRAPH_NAME = new UriRef("http://example.org/resource-resolver-log.graph");
    
    /**
     * This method return an RdfViewable, this is an RDF resource with associated
     * presentational information.
     */
    @GET
    public RdfViewable serviceEntry(@Context final UriInfo uriInfo, 
            @QueryParam("iri") final UriRef iri, 
            @HeaderParam("user-agent") String userAgent) throws Exception {
        final String resourcePath = uriInfo.getAbsolutePath().toString();
        //The URI at which this service was accessed accessed, this will be the 
        //central resource in the response
        final UriRef resource = new UriRef(resourcePath);
        //the in memory graph to which the triples for the response are added
        final MGraph responseGraph = new IndexedMGraph();
        //A union graph containing both the response specif triples as well 
        //as the log-graph
        final UnionMGraph resultGraph = new UnionMGraph(responseGraph, getLogGraph());
        //This GraphNode represents the service within our result graph
        final GraphNode node = new GraphNode(resource, resultGraph);
        //The triples will be added to the first graph of the union
        //i.e. to the in-memory responseGraph
        node.addProperty(RDF.type, Ontology.ResourceResolver);
        node.addProperty(RDFS.comment, new PlainLiteralImpl("A Resource Resolver"));
        if (iri != null) {
            node.addProperty(Ontology.describes, iri);
            addResourceDescription(iri, responseGraph);
            logRequest(iri, userAgent);
        }
        //What we return is the GraphNode we created with a template path
        return new RdfViewable("ServiceEntry", node, ResourceResolver.class);
    }
    
    @GET
    @Path("debug")
    public String serviceEntry() throws Exception {
        //Entity entity = entityhub.getEntity("http://www.products.com/Jewellery");
        Entity entity = siteManager.getEntity("http://dbpedia.org/resource/Paris");
        MGraph mGraph = new IndexedMGraph();
        RdfValueFactory valueFactory = new RdfValueFactory(mGraph);
        RdfRepresentation rdfRepresentation = valueFactory.toRdfRepresentation(entity.getRepresentation());
        UriRef entityUri = null;//rdfRepresentation.getNode();
        //rdfRepresentation.getRdfGraph().equals(mGraph);
        return "Entiy: "+entityUri+" in Graph: "+mGraph+" check "+rdfRepresentation.getRdfGraph().equals(mGraph);
        //FieldQuery query = siteManager.getQueryFactory().createFieldQuery();
        //FieldQuery query = new FieldQueryImpl();
        //final QueryResultList<Representation> result = siteManager.find(query);
        //return "executing query result: "+result.results();
    }

    /**
     * Add the description of a resource to the specified MGraph using SiteManager.
     * The description includes the metadata provided by the SiteManager.
     * 
     */
    private void addResourceDescription(UriRef iri, MGraph mGraph) {
        final Entity entity = siteManager.getEntity(iri.getUnicodeString());
        if (entity != null) {
            final RdfValueFactory valueFactory = new RdfValueFactory(mGraph);
            final Representation representation = entity.getRepresentation();
            if (representation != null) {
                valueFactory.toRdfRepresentation(representation);
            }
            final Representation metadata = entity.getMetadata();
            if (metadata != null) {
                valueFactory.toRdfRepresentation(metadata);
            }
        }
    }

    /**
     * Logs a request to the log-graph
     */
    private void logRequest(UriRef iri, String userAgent) {
        MGraph logGraph = getLogGraph();
        GraphNode loggedRequest = new GraphNode(new BNode(), logGraph);
        loggedRequest.addProperty(RDF.type, Ontology.LoggedRequest);
        loggedRequest.addPropertyValue(DC.date, new Date());
        loggedRequest.addPropertyValue(Ontology.userAgent, userAgent);
        loggedRequest.addProperty(Ontology.requestedEntity, iri);
    }

    /**
     * This either returns the existing MGraph for the log or creates a new one.
     * 
     * @return the MGraph to which the requests are logged
     */
    private MGraph getLogGraph() {
        try {
            return tcManager.getMGraph(LOG_GRAPH_NAME);
        } catch (NoSuchEntityException ex) {
            return tcManager.createMGraph(LOG_GRAPH_NAME);
        }
    }
    
}
