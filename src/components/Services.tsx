import { motion } from 'framer-motion';
import { openWhatsApp, waLink } from '../lib/whatsapp';

type Package = {
  name: string;
  nameAr: string;
  price: string;
  currency: string;
  description: string;
  features: string[];
  best: boolean;
  custom: boolean;
};

const packages: Package[] = [
  {
    name: 'Starter', nameAr: 'المبتدئ',
    price: '999', currency: 'AED',
    description: 'The perfect starting point for brands ready to elevate their visual presence.',
    features: ['15 professional photos', '2 cinematic videos / reels', 'Professional lighting setup', 'Full post-production editing', 'Social media optimized'],
    best: false, custom: false,
  },
  {
    name: 'Growth', nameAr: 'النمو',
    price: '1,799', currency: 'AED',
    description: 'Our most popular package — impactful storytelling with more content for growing brands.',
    features: ['30 professional photos', '4 cinematic videos / reels', 'Professional lighting setup', 'Full post-production editing', 'Social media optimized'],
    best: true, custom: false,
  },
  {
    name: 'Premium', nameAr: 'بريميوم',
    price: '2,999', currency: 'AED',
    description: 'A comprehensive visual experience with extensive coverage and revision flexibility.',
    features: ['50 professional photos', '6 cinematic videos / reels', '1 round of revisions', 'Professional lighting setup', 'Full post-production editing', 'Social media optimized'],
    best: false, custom: false,
  },
  {
    name: 'Monthly', nameAr: 'شهري',
    price: '4,000', currency: 'AED',
    description: 'Ongoing visual content with monthly site visits — ideal for consistent brand presence.',
    features: ['2 site visits per month', '60-80 professional photos', '8 cinematic videos', '1 round of revisions', 'Professional lighting setup', 'Full post-production editing', 'Social media optimized'],
    best: false, custom: false,
  },
  {
    name: 'Customized Service', nameAr: 'خدمة مخصصة',
    price: 'Bespoke', currency: '',
    description: "A fully tailored visual experience, designed around your brand's unique identity and goals.",
    features: ['Fully tailored to your needs', 'Flexible scope & delivery', 'Dedicated creative direction', 'Priority scheduling'],
    best: false, custom: true,
  },
];

export default function Services() {
  return (
    <section id="services" className="section-pad bg-[#080604] relative overflow-hidden">
      <div className="absolute top-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-[#d4a84b20] to-transparent" />
      <div className="absolute bottom-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-[#d4a84b20] to-transparent" />

      <div className="container-site">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.9 }}
          className="mb-6 text-center"
        >
          <div className="flex items-center justify-center gap-4 mb-6">
            <div className="w-6 h-px bg-[#d4a84b]" />
            <span className="font-sans text-[0.55rem] tracking-[0.55em] text-[#d4a84b] uppercase font-medium">Investment</span>
            <div className="w-6 h-px bg-[#d4a84b]" />
          </div>
          <h2 className="font-sans text-5xl md:text-6xl text-[#f0ebe0] mb-3 font-bold">Services &amp; Pricing</h2>
          <p className="font-sans text-[0.68rem] text-[#756f68] tracking-[0.18em] uppercase font-medium">Cafés &amp; restaurants across UAE</p>
          <div className="gold-rule mt-8" />
        </motion.div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-5 lg:gap-7 mt-14">
          {packages.slice(0, 3).map((pkg, i) => (
            <PricingCard key={`${pkg.name}-${i}`} pkg={pkg} index={i} />
          ))}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-5 lg:gap-7 mt-5 lg:mt-7 md:max-w-[66%] md:mx-auto">
          {packages.slice(3).map((pkg, i) => (
            <PricingCard key={pkg.name} pkg={pkg} index={i + 3} />
          ))}
        </div>

        <motion.p
          initial={{ opacity: 0 }}
          whileInView={{ opacity: 1 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8, delay: 0.4 }}
          className="font-sans text-[0.58rem] text-[#5f5850] tracking-[0.28em] uppercase text-center mt-16 font-medium"
        >
          On-location across Abu Dhabi &amp; Dubai &nbsp;&middot;&nbsp; All sessions by appointment
        </motion.p>
      </div>
    </section>
  );
}

function PricingCard({ pkg, index }: { pkg: Package; index: number }) {
  const msg = `Hello MIROIR Studio, I'm interested in the ${pkg.name} package.`;

  return (
    <motion.div
      initial={{ opacity: 0, y: 28 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.75, delay: index * 0.09, ease: [0.22, 1, 0.36, 1] }}
      className={`relative flex flex-col group transition-all duration-500 hover:-translate-y-1 ${
        pkg.best ? 'md:-mt-4 md:mb-4' : ''
      }`}
    >
      <div
        className={`relative flex flex-col flex-1 p-8 lg:p-9 border transition-colors duration-500 ${
          pkg.best
            ? 'border-[#d4a84b40] bg-[#0d0d0d]'
            : 'border-[#24201a] bg-[#0c0a08] hover:border-[#d4a84b30]'
        }`}
        style={{
          boxShadow: pkg.best
            ? '0 0 0 1px rgba(201,169,110,0.1), 0 24px 60px rgba(0,0,0,0.5), inset 0 1px 0 rgba(201,169,110,0.06)'
            : '0 14px 40px rgba(0,0,0,0.32)'
        }}
      >
        {pkg.best && (
          <div className="absolute top-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-[#d4a84b] to-transparent" />
        )}

        {pkg.best && (
          <div className="flex items-center gap-2 mb-7">
            <div className="w-3 h-px bg-[#d4a84b]" />
            <span className="font-sans text-[0.58rem] tracking-[0.32em] text-[#d4a84b] uppercase font-medium">Most Popular</span>
          </div>
        )}

        <div className="mb-7">
          <h3 className="font-sans text-2xl text-[#f0ebe0] leading-tight group-hover:text-white transition-colors duration-500 font-semibold">
            {pkg.name}
          </h3>
          <p className="font-sans text-lg text-[#d4a84b] mt-2 text-right font-normal" dir="rtl">{pkg.nameAr}</p>
        </div>

        <div className="mb-7 pb-7 border-b border-[#161616] transition-colors duration-500">
          {pkg.custom ? (
            <>
              <span className="font-sans text-3xl text-[#f0ebe0] font-bold">Bespoke</span>
              <p className="font-sans text-[0.58rem] tracking-[0.24em] text-[#645d55] uppercase mt-2 font-medium">Crafted to your vision</p>
            </>
          ) : (
            <div className="flex items-end gap-2">
              <span className={`font-sans text-5xl leading-none transition-colors duration-500 font-bold ${
                pkg.best ? 'text-[#d4a84b]' : 'text-[#9a8158] group-hover:text-[#d4a84b]'
              }`}>{pkg.price}</span>
              <span className="font-sans text-[0.58rem] text-[#665e56] tracking-[0.22em] uppercase pb-1.5 font-medium">{pkg.currency}</span>
            </div>
          )}
        </div>

        <p className="font-body text-[0.82rem] text-[#8a837b] leading-[1.85] mb-7 group-hover:text-[#aaa199] transition-colors duration-500 font-normal">
          {pkg.description}
        </p>

        <ul className="flex flex-col gap-3.5 mb-9 flex-1">
          {pkg.features.map((f) => (
            <li key={f} className="flex items-start gap-3.5">
              <div className={`w-3 h-px mt-[0.45rem] flex-shrink-0 transition-colors duration-500 ${
                pkg.best ? 'bg-[#d4a84b70]' : 'bg-[#463a2b] group-hover:bg-[#d4a84b40]'
              }`} />
              <span className="font-body text-[0.76rem] text-[#837d76] leading-relaxed group-hover:text-[#aaa199] transition-colors duration-500 font-normal">{f}</span>
            </li>
          ))}
        </ul>

        <a
          href={waLink(msg)}
          onClick={(e) => { e.preventDefault(); openWhatsApp(msg); }}
          className={`text-center font-sans text-[0.62rem] tracking-[0.24em] uppercase py-4 transition-all duration-300 cursor-pointer font-medium ${
            pkg.best
              ? 'bg-[#d4a84b] text-black hover:bg-[#d4b47a]'
              : 'border border-[#2a261f] text-[#8d857c] hover:border-[#d4a84b40] hover:text-[#d4a84b]'
          }`}
        >
          {pkg.custom ? 'Discuss Your Project' : 'Enquire Now'}
        </a>

        {pkg.best && (
          <div className="absolute bottom-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-[#d4a84b30] to-transparent" />
        )}
      </div>
    </motion.div>
  );
}
