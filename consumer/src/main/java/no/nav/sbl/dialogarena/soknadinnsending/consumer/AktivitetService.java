package no.nav.sbl.dialogarena.soknadinnsending.consumer;

import no.nav.sbl.dialogarena.sendsoknad.domain.Faktum;
import no.nav.sbl.dialogarena.sendsoknad.domain.util.ServiceUtils;
import no.nav.tjeneste.virksomhet.sakogaktivitet.v1.*;
import no.nav.tjeneste.virksomhet.sakogaktivitet.v1.informasjon.*;
import no.nav.tjeneste.virksomhet.sakogaktivitet.v1.meldinger.WSFinnAktivitetOgVedtakDagligReiseListeRequest;
import no.nav.tjeneste.virksomhet.sakogaktivitet.v1.meldinger.WSFinnAktivitetOgVedtakDagligReiseListeResponse;
import no.nav.tjeneste.virksomhet.sakogaktivitet.v1.meldinger.WSFinnAktivitetsinformasjonListeRequest;
import no.nav.tjeneste.virksomhet.sakogaktivitet.v1.meldinger.WSFinnAktivitetsinformasjonListeResponse;
import org.joda.time.LocalDate;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Service
public class AktivitetService {

    private static final Predicate<Faktum> BARE_AKTIVITETER_SOM_KAN_HA_STONADER = faktum ->
            faktum.harPropertySomMatcher("erStoenadsberettiget", "true");
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AktivitetService.class);

    @Inject
    @Named("sakOgAktivitetEndpoint")
    private SakOgAktivitetV1 aktivitetWebService;





    public List<Faktum> hentAktiviteter(String fodselnummer) {
        try {
            WSFinnAktivitetsinformasjonListeResponse aktiviteter = aktivitetWebService.finnAktivitetsinformasjonListe(lagAktivitetsRequest(fodselnummer));
            if (aktiviteter == null) {
                return Collections.emptyList();
            }
            return aktiviteter.getAktivitetListe().stream()
                    .map(new AktiviteterTransformer())
                    .filter(BARE_AKTIVITETER_SOM_KAN_HA_STONADER)
                    .collect(toList());

        } catch (FinnAktivitetsinformasjonListePersonIkkeFunnet e) {
            LOG.debug("person ikke funnet i arena: " + fodselnummer + ": " + e, e);
            return Collections.emptyList();
        } catch (FinnAktivitetsinformasjonListeSikkerhetsbegrensning e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public List<Faktum> hentVedtak(String fodselsnummer) {
        try {
            WSFinnAktivitetOgVedtakDagligReiseListeRequest request = new WSFinnAktivitetOgVedtakDagligReiseListeRequest()
                    .withPersonident(fodselsnummer)
                    .withPeriode(new WSPeriode().withFom(LocalDate.now().minusMonths(6)).withTom(LocalDate.now().plusMonths(2)));
            WSFinnAktivitetOgVedtakDagligReiseListeResponse response = aktivitetWebService.finnAktivitetOgVedtakDagligReiseListe(request);

            if (response == null) {
                return Collections.emptyList();
            }
            return response.getAktivitetOgVedtakListe().stream().flatMap(new VedtakTransformer()).collect(toList());

        } catch (FinnAktivitetOgVedtakDagligReiseListePersonIkkeFunnet e) {
            LOG.debug("person ikke funnet i arena: " + fodselsnummer + ": " + e, e);
            return Collections.emptyList();
        } catch (FinnAktivitetOgVedtakDagligReiseListeSikkerhetsbegrensning e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private WSFinnAktivitetsinformasjonListeRequest lagAktivitetsRequest(String fodselnummer) {
        return new WSFinnAktivitetsinformasjonListeRequest()
                .withPersonident(fodselnummer)
                .withPeriode(new WSPeriode().withFom(LocalDate.now().minusMonths(6)).withTom(LocalDate.now().plusMonths(2)));
    }


    private static class AktiviteterTransformer implements Function<WSAktivitet, Faktum> {

        @Override
        public Faktum apply(WSAktivitet wsAktivitet) {
            Faktum faktum = new Faktum()
                    .medKey("aktivitet")
                    .medProperty("id", wsAktivitet.getAktivitetId())
                    .medProperty("navn", wsAktivitet.getAktivitetsnavn())
                    .medProperty("type", wsAktivitet.getAktivitetstype().getValue())
                    .medProperty("arrangoer", wsAktivitet.getArrangoer());

            WSPeriode periode = wsAktivitet.getPeriode();
            faktum.medProperty("fom", ServiceUtils.datoTilString(periode.getFom()));
            faktum.medProperty("tom", ServiceUtils.datoTilString(periode.getTom()));
            faktum.medProperty("erStoenadsberettiget", "" + wsAktivitet.isErStoenadsberettigetAktivitet());

            return faktum;
        }

    }

    private static class VedtakTransformer implements Function<WSAktivitetOgVedtak, Stream<Faktum>> {
        @Override
        public Stream<Faktum> apply(WSAktivitetOgVedtak wsAktivitetOgVedtak) {
            return wsAktivitetOgVedtak.getSaksinformasjon().getVedtaksinformasjon().stream()
                    .map(wsVedtaksinformasjon -> transformerTilFaktum(wsVedtaksinformasjon, wsAktivitetOgVedtak));

        }

        private Faktum transformerTilFaktum(WSVedtaksinformasjon input, WSAktivitetOgVedtak aktivitet) {
            Faktum faktum = new Faktum()
                    .medKey("vedtak")
                    .medProperty("aktivitetId", aktivitet.getAktivitetId())
                    .medProperty("aktivitetNavn", aktivitet.getAktivitetsnavn())
                    .medProperty("tema", hentTema(aktivitet.getSaksinformasjon()))
                    .medProperty("erStoenadsberettiget", "" + aktivitet.isErStoenadsberettigetAktivitet())
                    .medProperty("forventetDagligParkeringsutgift", ServiceUtils.nullToBlank(input.getForventetDagligParkeringsutgift()))
                    .medProperty("dagsats", ServiceUtils.nullToBlank(input.getDagsats()))
                    .medProperty("trengerParkering", ServiceUtils.nullToBlank(input.isTrengerParkering()))
                    .medProperty("id", input.getVedtakId());
            WSPeriode periode = aktivitet.getPeriode();
            faktum.medProperty("aktivitetFom", ServiceUtils.datoTilString(periode.getFom()));
            faktum.medProperty("aktivitetTom", ServiceUtils.datoTilString(periode.getTom()));

            periode = input.getPeriode();
            faktum.medProperty("fom", ServiceUtils.datoTilString(periode.getFom()));
            faktum.medProperty("tom", ServiceUtils.datoTilString(periode.getTom()));

            return faktum;
        }

        private String hentTema(WSSaksinformasjon saksinformasjon) {
            return Optional.ofNullable(saksinformasjon)
                    .map(WSSaksinformasjon::getSakstype)
                    .map(WSKodeverdi::getValue)
                    .orElse("TSO");
        }
    }

}
