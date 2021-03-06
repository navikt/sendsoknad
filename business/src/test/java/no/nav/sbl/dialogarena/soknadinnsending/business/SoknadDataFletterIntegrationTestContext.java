package no.nav.sbl.dialogarena.soknadinnsending.business;

import no.nav.sbl.dialogarena.sendsoknad.domain.message.TekstHenter;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.RepositoryTestSupport;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.TestSupport;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.config.DatabaseTestContext;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.soknad.HendelseRepository;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.soknad.HendelseRepositoryJdbc;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.soknad.SoknadRepository;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.soknad.SoknadRepositoryJdbc;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.vedlegg.VedleggRepository;
import no.nav.sbl.dialogarena.soknadinnsending.business.db.vedlegg.VedleggRepositoryJdbc;
import no.nav.sbl.dialogarena.soknadinnsending.business.service.FaktaService;
import no.nav.sbl.dialogarena.soknadinnsending.business.service.MigrasjonHandterer;
import no.nav.sbl.dialogarena.soknadinnsending.business.service.VedleggService;
import no.nav.sbl.dialogarena.soknadinnsending.business.service.soknadservice.*;
import no.nav.sbl.dialogarena.soknadinnsending.consumer.fillager.FillagerService;
import no.nav.sbl.dialogarena.soknadinnsending.consumer.henvendelse.HenvendelseService;
import no.nav.sbl.dialogarena.soknadinnsending.consumer.skjemaoppslag.SkjemaOppslagService;
import no.nav.tjeneste.domene.brukerdialog.fillager.v1.FilLagerPortType;
import no.nav.tjeneste.domene.brukerdialog.henvendelse.v2.henvendelse.HenvendelsePortType;
import no.nav.tjeneste.domene.brukerdialog.sendsoknad.v1.SendSoknadPortType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.time.Clock;

import static org.mockito.Mockito.mock;

@Import(value = {DatabaseTestContext.class})
@EnableTransactionManagement()
@Configuration
public class SoknadDataFletterIntegrationTestContext {
    @Inject
    private DataSource dataSource;

    @Bean
    public Clock clock(){ return Clock.systemDefaultZone(); }

    @Bean
    public MigrasjonHandterer migrasjonHandterer() {return new MigrasjonHandterer();}

    @Bean
    public SoknadDataFletter fletter() {
        return new SoknadDataFletter();
    }

    @Bean
    public AlternativRepresentasjonService alternativRepresentasjonService() {
        return new AlternativRepresentasjonService();
    }

    @Bean
    public HenvendelseService henvendelseService() {
        return new HenvendelseService();
    }

    @Bean
    public SendSoknadPortType sendSoknadEndpoint() {
        return mock(SendSoknadPortType.class);
    }

    @Bean
    public SendSoknadPortType sendSoknadSelftestEndpoint() {
        return mock(SendSoknadPortType.class);
    }

    @Bean
    public HenvendelsePortType henvendelseEndpoint() {
        return mock(HenvendelsePortType.class);
    }

    @Bean
    public FillagerService fillagerService() {
        return new FillagerService();
    }

    @Bean
    public FilLagerPortType fillagerEndpoint() {
        return mock(FilLagerPortType.class);
    }

    @Bean
    public FilLagerPortType fillagerSelftestEndpoint() {
        return mock(FilLagerPortType.class);
    }

    @Bean
    public VedleggService vedleggService() {
        return new VedleggService();
    }

    @Bean
    public SoknadRepository soknadInnsendingRepository() {
        return new SoknadRepositoryJdbc();
    }

    @Bean
    public HendelseRepository hendelseRepository() {
        return new HendelseRepositoryJdbc();
    }

    @Bean
    public VedleggRepository vedleggRepository() {
        return new VedleggRepositoryJdbc();
    }

    @Bean
    public RepositoryTestSupport testSupport() {
        return new TestSupport(dataSource);
    }

    @Bean
    public SkjemaOppslagService skjemaOppslagService() {
        return mock(SkjemaOppslagService.class);
    }

    @Bean
    public SoknadService soknadService() {
        return new SoknadService();
    }

    @Bean
    public WebSoknadConfig webSoknadConfig() {
        return new WebSoknadConfig();
    }

    @Bean
    public FaktaService faktaService(){
        return new FaktaService();
    }

    @Bean
    public TekstHenter tekstHenter(){
        return new TekstHenter();
    }

    @Bean
    public EttersendingService ettersendingService() { return new EttersendingService(); }

    @Bean
    public SoknadMetricsService metricsService() {
        return mock(SoknadMetricsService.class);
    }

    @Bean
    public MetricsEventFactory metricsEventFactory() {
        return new MetricsEventFactory();
    }
}
