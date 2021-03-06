package no.nav.sbl.dialogarena.soknadinnsending.business.service.soknadservice;

import no.nav.melding.domene.brukerdialog.behandlingsinformasjon.v1.XMLHovedskjema;
import no.nav.melding.domene.brukerdialog.behandlingsinformasjon.v1.XMLMetadata;
import no.nav.melding.domene.brukerdialog.behandlingsinformasjon.v1.XMLMetadataListe;
import no.nav.sbl.dialogarena.sendsoknad.domain.Faktum;
import no.nav.sbl.dialogarena.sendsoknad.domain.SoknadInnsendingStatus;
import no.nav.sbl.dialogarena.sendsoknad.domain.WebSoknad;
import no.nav.sbl.dialogarena.sendsoknad.domain.exception.SendSoknadException;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.soknad.SoknadRepository;
import no.nav.sbl.dialogarena.soknadinnsending.business.service.FaktaService;
import no.nav.sbl.dialogarena.soknadinnsending.business.service.VedleggService;
import no.nav.sbl.dialogarena.soknadinnsending.consumer.henvendelse.HenvendelseService;
import no.nav.tjeneste.domene.brukerdialog.sendsoknad.v1.meldinger.WSBehandlingskjedeElement;
import no.nav.tjeneste.domene.brukerdialog.sendsoknad.v1.meldinger.WSHentSoknadResponse;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Optional;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static no.nav.sbl.dialogarena.sendsoknad.domain.Faktum.FaktumType.SYSTEMREGISTRERT;
import static no.nav.sbl.dialogarena.sendsoknad.domain.SoknadInnsendingStatus.AVBRUTT_AV_BRUKER;
import static no.nav.sbl.dialogarena.soknadinnsending.business.service.soknadservice.StaticMetoder.*;

@Component
public class EttersendingService {

    @Inject
    private HenvendelseService henvendelseService;
    @Inject
    private VedleggService vedleggService;
    @Inject
    private FaktaService faktaService;
    @Inject
    @Named("soknadInnsendingRepository")
    private SoknadRepository lokalDb;
    @Inject
    private SoknadMetricsService soknadMetricsService;

    public String start(String behandlingsIdDetEttersendesPaa, String aktorId) {
        List<WSBehandlingskjedeElement> behandlingskjede = henvendelseService.hentBehandlingskjede(behandlingsIdDetEttersendesPaa);
        WSHentSoknadResponse nyesteSoknad = hentNyesteSoknadFraHenvendelse(behandlingskjede);

        Optional.ofNullable(nyesteSoknad.getInnsendtDato()).orElseThrow(() -> new SendSoknadException("Kan ikke starte ettersending på en ikke fullfort soknad"));

        String nyBehandlingsId = henvendelseService.startEttersending(nyesteSoknad, aktorId);
        String behandlingskjedeId = Optional.ofNullable(nyesteSoknad.getBehandlingskjedeId()).orElse(nyesteSoknad.getBehandlingsId());
        WebSoknad ettersending = lagreEttersendingTilLokalDb(behandlingsIdDetEttersendesPaa, behandlingskjede, behandlingskjedeId, nyBehandlingsId, aktorId);

        soknadMetricsService.startetSoknad(ettersending.getskjemaNummer(), true);

        return ettersending.getBrukerBehandlingId();
    }

    private WSHentSoknadResponse hentNyesteSoknadFraHenvendelse(List<WSBehandlingskjedeElement> behandlingskjede) {
        List<WSBehandlingskjedeElement> nyesteForstBehandlinger = behandlingskjede.stream()
                .filter(element -> AVBRUTT_AV_BRUKER != SoknadInnsendingStatus.valueOf(element.getStatus()))
                .sorted(NYESTE_FORST)
                .collect(toList());

        return henvendelseService.hentSoknad(nyesteForstBehandlinger.get(0).getBehandlingsId());
    }

    private WebSoknad lagreEttersendingTilLokalDb(String originalBehandlingsId, List<WSBehandlingskjedeElement> behandlingskjede,
                                                  String behandlingskjedeId, String ettersendingsBehandlingId, String aktorId) {
        List<XMLMetadata> alleVedlegg = ((XMLMetadataListe) henvendelseService.hentSoknad(ettersendingsBehandlingId).getAny()).getMetadata();
        List<XMLMetadata> vedleggBortsettFraKvittering = alleVedlegg.stream().filter(IKKE_KVITTERING).collect(toList());

        WebSoknad ettersending = lagSoknad(ettersendingsBehandlingId, behandlingskjedeId, finnHovedskjema(vedleggBortsettFraKvittering), aktorId);

        Long soknadId = lokalDb.opprettSoknad(ettersending);
        ettersending.setSoknadId(soknadId);

        DateTime originalInnsendtDato = hentOrginalInnsendtDato(behandlingskjede, originalBehandlingsId);
        faktaService.lagreSystemFaktum(soknadId, soknadInnsendingsDato(soknadId, originalInnsendtDato));

        vedleggService.hentVedleggOgPersister(new XMLMetadataListe(vedleggBortsettFraKvittering), soknadId);
        return ettersending;
    }

    private Faktum soknadInnsendingsDato(Long soknadId, DateTime innsendtDato) {
        return new Faktum()
                .medSoknadId(soknadId)
                .medKey("soknadInnsendingsDato")
                .medValue(String.valueOf(innsendtDato.getMillis()))
                .medType(SYSTEMREGISTRERT);
    }

    private WebSoknad lagSoknad(String behandlingsId, String behandlingskjedeId, XMLHovedskjema hovedskjema, String aktorId) {
        return WebSoknad.startEttersending(behandlingsId)
                .medUuid(randomUUID().toString())
                .medAktorId(aktorId)
                .medskjemaNummer(hovedskjema.getSkjemanummer())
                .medBehandlingskjedeId(behandlingskjedeId)
                .medJournalforendeEnhet(hovedskjema.getJournalforendeEnhet());
    }

    private XMLHovedskjema finnHovedskjema(List<XMLMetadata> vedleggBortsettFraKvittering) {

        return vedleggBortsettFraKvittering.stream()
                .filter(xmlMetadata -> xmlMetadata instanceof XMLHovedskjema)
                .map(xmlMetadata -> (XMLHovedskjema) xmlMetadata)
                .findFirst()
                .orElseThrow(() -> new SendSoknadException("Kunne ikke hente opp hovedskjema for søknad"));
    }
}
