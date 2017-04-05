/*Copyright (c) 2016 "hbz"

This file is part of zettel.

zettel is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package controllers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;

import org.openrdf.rio.RDFFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import models.Article;
import models.Chapter;
import models.Proceeding;
import models.ResearchData;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import services.RdfUtils;
import services.XmlUtils;
import services.ZettelHelper;
import services.ZettelModel;
import services.ZettelRegister;
import services.ZettelRegisterEntry;
import views.html.*;
import play.libs.ws.*;

/**
 * @author Jan Schnasse
 *
 */
public class ZettelController extends Controller {
	@Inject
	play.data.FormFactory formFactory;

	@Inject
	WSClient ws;

	/**
	 * @return the start page
	 */
	public CompletionStage<Result> index() {
		CompletableFuture<Result> future = new CompletableFuture<>();
		future.complete(ok(index.render("Zettel")));
		return future;
	}

	/**
	 * @param all a path
	 * @return a header for all routes when ask with HTTP OPTIONS
	 */
	public CompletionStage<Result> corsforall(String all) {
		CompletableFuture<Result> future = new CompletableFuture<>();
		response().setHeader("Access-Control-Allow-Origin", "*");
		response().setHeader("Allow", "*");
		response().setHeader("Access-Control-Allow-Methods",
				"POST, GET, PUT, DELETE, OPTIONS");
		response().setHeader("Access-Control-Allow-Headers",
				"Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent");
		future.complete(ok());
		return future;
	}

	/**
	 * @param id if null list all available forms otherwise render the requested
	 *          form.
	 * @param format ask for certain format. supports xml,ntriples and json
	 * @param documentId your personal id for the document you want to create form
	 *          data for
	 * @param topicId the topic id is used by our regal-drupal to find the actual
	 *          documentId. You can probably ignore this.
	 * @return a list of available forms or if present the form with a certain id.
	 * 
	 */
	public CompletionStage<Result> getForms(String id, String format,
			String documentId, String topicId) {
		setHeaders();
		CompletableFuture<Result> future = new CompletableFuture<>();
		Result result = null;
		if (id == null)
			result = listForms();
		else {
			ZettelRegister zettelRegister = new ZettelRegister();
			ZettelRegisterEntry zettel = zettelRegister.get(id);
			result = renderForm(zettel, format, documentId, topicId);
		}
		future.complete(result);
		return future;
	}

	/**
	 * @param id the id of the form the POST data is send to.
	 * @param format ask for certain format. supports xml,ntriples and json
	 * @param documentId your personal id for the document you want to create form
	 *          data for
	 * @param topicId the topic id is used by our regal-drupal to find the actual
	 *          documentId. You can probably ignore this.
	 * @return if posted with accept:application/json return json-ld
	 *         representation of the data. In all other cases return an html
	 *         formular.
	 */
	public CompletionStage<Result> postForm(String id, String format,
			String documentId, String topicId) {
		setHeaders();
		Result result = null;

		// play.Logger.debug("Content of request-------------\n"
		// + ZettelHelper.objectToString(request().body().asText()));
		// play.Logger.debug("Content of model-------------\n"
		// + ZettelHelper.objectToString(zettel.getModel()));
		play.Logger.debug(String.format("Content of request\n%s\n%s", request(),
				ZettelHelper.objectToString(request().body().asFormUrlEncoded())));

		ZettelRegister zettelRegister = new ZettelRegister();
		CompletableFuture<Result> future = new CompletableFuture<>();
		ZettelRegisterEntry zettel = zettelRegister.get(id);
		Form<?> form = bindToForm(zettel, documentId, topicId);
		result = renderForm(format, documentId, topicId, zettel, form);
		future.complete(result);

		return future;
	}

	private String printRdf(Form<?> form) {
		try {
			String rdfString = RdfUtils.readRdfToString(
					new ByteArrayInputStream(
							((ZettelModel) form.get()).toString().getBytes("utf-8")),
					RDFFormat.JSONLD, RDFFormat.RDFXML, "");
			return rdfString;
		} catch (Exception e) {
			play.Logger.debug("", e);
			return "";
		}
	}

	/**
	 * @param format ask for certain format. supports xml,ntriples and json
	 * @param documentId your personal id for the document you want to create form
	 *          data for
	 * @param topicId the topic id is used by our regal-drupal to find the actual
	 *          documentId. You can probably ignore this.
	 * @return a client demo
	 */
	public CompletionStage<Result> client(String format, String id,
			String documentId, String topicId) {
		CompletableFuture<Result> future = new CompletableFuture<>();
		future
				.complete(ok(client.render("Zettel", format, id, documentId, topicId)));
		return future;
	}

	private static Result renderForm(String format, String documentId,
			String topicId, ZettelRegisterEntry zettel, Form<?> form) {
		Result result;
		if (form.hasErrors()) {
			if (request().accepts("text/html")) {
				result = badRequest(zettel.render(form, format, documentId, topicId));
			} else {
				result = badRequest(form.errorsAsJson()).as("application/json");
			}
		} else {
			if (request().accepts("text/html")) {
				result = ok(zettel.render(form, format, documentId, topicId));
			} else {
				result = ok(form.get().toString()).as("application/json");
			}
			play.Logger.debug(String.format("Content of model\n%s",
					((ZettelModel) form.get()).print()));
		}
		return result;
	}

	private Form<?> bindToForm(ZettelRegisterEntry zettel, String documentId,
			String topicId) {
		Form<?> form = null;
		if ("application/rdf+xml".equals(request().contentType().get())) {
			play.Logger.debug("Load form from rdf");
			form = loadRdf(XmlUtils.docToString(request().body().asXml()), zettel,
					documentId, topicId);
			form.bindFromRequest();
		} else if ("application/x-www-form-urlencoded"
				.equals(request().contentType().get())) {
			play.Logger.debug("Load form from request");
			form = formFactory.form(zettel.getModel().getClass()).bindFromRequest();
		} else {
			play.Logger
					.error("WARN: Can not handle " + request().contentType().get());
		}
		play.Logger
				.debug(String.format("Content of rdf result\n%s", printRdf(form)));
		return form;
	}

	private Form<?> loadRdf(String asText, ZettelRegisterEntry zettel,
			String documentId, String topicId) {
		try (InputStream in = new ByteArrayInputStream(asText.getBytes("utf-8"))) {
			String id = zettel.getId();
			if (ResearchData.id.equals(id)) {
				return formFactory.form(ResearchData.class)
						.fill((ResearchData) zettel.getModel().deserializeFromRdf(in,
								RDFFormat.RDFXML, documentId, topicId));
			} else if (Article.id.equals(id)) {
				return formFactory.form(Article.class).fill((Article) zettel.getModel()
						.deserializeFromRdf(in, RDFFormat.RDFXML, documentId, topicId));
			} else if (Proceeding.id.equals(id)) {
				return formFactory.form(Proceeding.class)
						.fill((Proceeding) zettel.getModel().deserializeFromRdf(in,
								RDFFormat.RDFXML, documentId, topicId));
			} else if (Chapter.id.equals(id)) {
				return formFactory.form(Chapter.class).fill((Chapter) zettel.getModel()
						.deserializeFromRdf(in, RDFFormat.RDFXML, documentId, topicId));
			}
			return null;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void setHeaders() {
		response().setHeader("Access-Control-Allow-Origin", "*");
		response().setHeader("Access-Control-Allow-Headers",
				"Origin, X-Requested-With, Content-Type, Accept");
	}

	private static Result listForms() {
		ZettelRegister zettelRegister = new ZettelRegister();
		List<String> formList = zettelRegister.getIds();
		return ok(forms.render(formList));
	}

	private Result renderForm(ZettelRegisterEntry zettel, String format,
			String documentId, String topicId) {
		Form<?> form = formFactory.form(zettel.getModel().getClass());
		return ok(zettel.render(form, format, documentId, topicId));
	}

	/**
	 * @param q the query will be redirected to geonames
	 * @return the response from api.geonames.org
	 */
	public CompletionStage<Result> geoSearch(String q) {
		String geoNamesUrl = "http://api.geonames.org/searchJSON";
		WSRequest request = ws.url(geoNamesUrl);
		WSRequest complexRequest =
				request.setRequestTimeout(1000).setQueryParameter("q", q)
						.setQueryParameter("username", "epublishinghbz");
		return complexRequest.setFollowRedirects(true).get()
				.thenApply(response -> ok(response.asJson()));
	}

	public CompletionStage<Result> orcidSearch(String q) {
		String orcidUrl = "http://pub.orcid.org/search/orcid-bio";
		WSRequest request = ws.url(orcidUrl);
		WSRequest complexRequest =
				request.setRequestTimeout(1000).setQueryParameter("q", q);
		return complexRequest.setFollowRedirects(true).get()
				.thenApply(response -> ok(response.asJson()));
	}

	public CompletionStage<Result> orcidAutocomplete(String q) {
		final String[] callback =
				request() == null || request().queryString() == null ? null
						: request().queryString().get("callback");
		String orcidUrl = "http://pub.orcid.org/search/orcid-bio";
		WSRequest request = ws.url(orcidUrl);
		WSRequest complexRequest = request.setHeader("accept", "application/json")
				.setRequestTimeout(5000).setQueryParameter("q", q);
		return complexRequest.setFollowRedirects(true).get().thenApply(response -> {
			JsonNode hits =
					response.asJson().at("/orcid-search-results/orcid-search-result");
			List<Map<String, String>> result = new ArrayList<>();
			hits.forEach((hit) -> {
				String label = hit
						.at("/orcid-profile/orcid-bio/personal-details/family-name/value")
						.asText()
						+ ", "
						+ hit
								.at("/orcid-profile/orcid-bio/personal-details/given-names/value")
								.asText();
				String id = hit.at("/orcid-profile/orcid-identifier/uri").asText();
				Map<String, String> m = new HashMap<>();
				m.put("label", label);
				m.put("value", id);
				result.add(m);
			});
			String searchResult = ZettelHelper.objectToString(result);
			String myResponse = callback != null
					? String.format("/**/%s(%s)", callback[0], searchResult)
					: searchResult;
			return ok(myResponse);
		});
	}

	/**
	 * @param q a query against lobid
	 * @return a jsonp result
	 */
	public CompletionStage<Result> journalAutocomplete(String q) {
		final String[] callback =
				request() == null || request().queryString() == null ? null
						: request().queryString().get("callback");
		String lobidUrl = "http://lobid.org/resources/search";
		WSRequest request = ws.url(lobidUrl);
		WSRequest complexRequest =
				request.setHeader("accept", "application/json").setRequestTimeout(5000)
						.setQueryParameter("q", q + " AND zdbId:* AND issn:*");
		return complexRequest.setFollowRedirects(true).get().thenApply(response -> {
			JsonNode hits = response.asJson().at("/member");
			List<Map<String, String>> result = new ArrayList<>();
			hits.forEach((hit) -> {
				String title = hit.at("/title").asText();
				String publisher =
						hit.at("/publication").get(0).at("/publishedBy").asText();
				String issn = "";
				JsonNode issns = hit.at("/issn");
				StringBuffer issn_c = new StringBuffer();
				issns.forEach((issn_i) -> {
					issn_c.append(issn_i.asText() + ",");
				});
				issn = issn_c.substring(0, issn_c.length() - 1);
				StringBuilder label = new StringBuilder(title);
				if (issn != null && !issn.isEmpty())
					label.insert(0, issn + " - ");
				if (publisher != null && !publisher.isEmpty())
					label.append(" - Hrsg.: " + publisher);
				String id = hit.at("/id").asText();
				Map<String, String> m = new HashMap<>();
				m.put("label", label.toString());
				m.put("value", id);
				result.add(m);
			});
			String searchResult = ZettelHelper.objectToString(result);
			String myResponse = callback != null
					? String.format("/**/%s(%s)", callback[0], searchResult)
					: searchResult;
			return ok(myResponse);
		});
	}

	/**
	 * @param q a query against lobid
	 * @return a jsonp result
	 */
	public CompletionStage<Result> subjectAutocomplete(String q) {
		final String[] callback =
				request() == null || request().queryString() == null ? null
						: request().queryString().get("callback");
		String lobidUrl = "http://lobid.org/subject";
		WSRequest request = ws.url(lobidUrl);
		WSRequest complexRequest = request.setHeader("accept", "application/json")
				.setRequestTimeout(5000).setQueryParameter("q", q)
				.setQueryParameter("t",
						"http://d-nb.info/standards/elementset/gnd#SubjectHeadingSensoStricto");
		return complexRequest.setFollowRedirects(true).get().thenApply(response -> {
			JsonNode hits = response.asJson();
			List<Map<String, String>> result = new ArrayList<>();
			hits.forEach((hit) -> {
				StringBuffer label = new StringBuffer();
				JsonNode graph = hit.at("/@graph");
				graph.forEach((g) -> {
					label.append(g.at("/gndIdentifier").asText() + " - ");
					JsonNode prefName = g.at("/preferredNameForTheSubjectHeading");
					if (prefName.isArray()) {
						prefName.forEach((p) -> {
							label.append(p.asText() + ",");
						});
						label.deleteCharAt(label.length() - 1);
					} else {
						label.append(prefName.asText());
					}
				});
				String id = hit.at("/primaryTopic").asText();
				Map<String, String> m = new HashMap<>();
				m.put("label", label.toString());
				m.put("value", id);
				result.add(m);
			});
			result.remove(0);
			String searchResult = ZettelHelper.objectToString(result);
			String myResponse = callback != null
					? String.format("/**/%s(%s)", callback[0], searchResult)
					: searchResult;
			return ok(myResponse);
		});
	}

	/**
	 * @param q a query against lobid
	 * @return a jsonp result
	 */
	public CompletionStage<Result> personAutocomplete(String q) {
		final String[] callback =
				request() == null || request().queryString() == null ? null
						: request().queryString().get("callback");
		String lobidUrl = "http://lobid.org/subject";
		WSRequest request = ws.url(lobidUrl);
		WSRequest complexRequest = request.setHeader("accept", "application/json")
				.setRequestTimeout(5000).setQueryParameter("q", q).setQueryParameter(
						"t", "http://d-nb.info/standards/elementset/gnd#Person");
		return complexRequest.setFollowRedirects(true).get().thenApply(response -> {
			JsonNode hits = response.asJson();
			List<Map<String, String>> result = new ArrayList<>();
			hits.forEach((hit) -> {
				StringBuffer label = new StringBuffer();
				JsonNode graph = hit.at("/@graph");
				graph.forEach((g) -> {

					label.append(g.at("/gndIdentifier").asText() + " - ");
					JsonNode prefName = g.at("/preferredNameForThePerson");
					if (prefName.isArray()) {
						prefName.forEach((p) -> {
							label.append(p.asText() + ",");
						});
						label.deleteCharAt(label.length() - 1);
					} else {
						label.append(prefName.asText());
					}
					JsonNode dob = g.at("/dataOfBirth/@value");
					JsonNode dod = g.at("/dateOfDeath/@value");
					JsonNode prof = g.at("/professionOrOccupation:AsLiteral");

					if (!dob.asText().isEmpty())
						label.append(" " + dob.asText() + "-");
					if (!dod.asText().isEmpty())
						label.append(" -" + dod.asText());
					// label.append(prof.asText());
				});

				String id = hit.at("/primaryTopic").asText();
				Map<String, String> m = new HashMap<>();
				m.put("label", label.toString());
				m.put("value", id);
				result.add(m);
			});
			result.remove(0);
			String searchResult = ZettelHelper.objectToString(result);
			String myResponse = callback != null
					? String.format("/**/%s(%s)", callback[0], searchResult)
					: searchResult;
			return ok(myResponse);
		});
	}

	/**
	 * @param q a query against lobid
	 * @return a jsonp result
	 */
	public CompletionStage<Result> corporateBodyAutocomplete(String q) {
		final String[] callback =
				request() == null || request().queryString() == null ? null
						: request().queryString().get("callback");
		String lobidUrl = "http://lobid.org/subject";
		WSRequest request = ws.url(lobidUrl);
		WSRequest complexRequest = request.setHeader("accept", "application/json")
				.setRequestTimeout(5000).setQueryParameter("q", q).setQueryParameter(
						"t", "http://d-nb.info/standards/elementset/gnd#CorporateBody");
		return complexRequest.setFollowRedirects(true).get().thenApply(response -> {
			JsonNode hits = response.asJson();
			List<Map<String, String>> result = new ArrayList<>();
			hits.forEach((hit) -> {
				StringBuffer label = new StringBuffer();
				JsonNode graph = hit.at("/@graph");
				graph.forEach((g) -> {

					label.append(g.at("/gndIdentifier").asText() + " - ");
					JsonNode prefName = g.at("/preferredNameForTheCorporateBody");
					if (prefName.isArray()) {
						prefName.forEach((p) -> {
							label.append(p.asText() + ",");
						});
						label.deleteCharAt(label.length() - 1);
					} else {
						label.append(prefName.asText());
					}
					JsonNode dob = g.at("/dateOfEstablishment/@value");
					JsonNode dod = g.at("/XX.XX.2003/@value");
					JsonNode prof = g.at("/professionOrOccupation:AsLiteral");

					if (!dob.asText().isEmpty())
						label.append(" " + dob.asText() + "-");
					if (!dod.asText().isEmpty())
						label.append(" -" + dod.asText());
					// label.append(prof.asText());
				});

				String id = hit.at("/primaryTopic").asText();
				Map<String, String> m = new HashMap<>();
				m.put("label", label.toString());
				m.put("value", id);
				result.add(m);
			});
			result.remove(0);
			String searchResult = ZettelHelper.objectToString(result);
			String myResponse = callback != null
					? String.format("/**/%s(%s)", callback[0], searchResult)
					: searchResult;
			return ok(myResponse);
		});
	}
}
