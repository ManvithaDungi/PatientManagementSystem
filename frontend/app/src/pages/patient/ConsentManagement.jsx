import React, { useState, useMemo, useEffect } from 'react';
import {
   Shield, Info, CheckCircle, Clock, XCircle, ChevronDown, ChevronUp,
   Stethoscope, FlaskConical, Users, Network, Bell, Megaphone, BookOpen, Lock,
   Download, AlertTriangle, Check, X, HelpCircle, Mail, Phone, MessageSquare,
   FileText, ExternalLink, Edit3, Trash2, Plus, Calendar, User, History,
   Database, Eye, Settings, RefreshCw, Archive, Search, Filter, BarChart3
} from 'lucide-react';
import Card from '../../components/common/Card';
import Button from '../../components/common/Button';
import IconOnlyButton from '../../components/common/IconOnlyButton';
import Badge from '../../components/common/Badge';
import Modal from '../../components/common/Modal';
import GrantModifyConsent from './GrantModifyConsent';
import NewConsentModal from './NewConsentModal';
import { useAuth } from '../../contexts/AuthContext';
import { consentAPI } from '../../services/api';

const ConsentManagement = () => {
   const { user } = useAuth();
   // eslint-disable-next-line no-unused-vars
   const patientId = user?.userId;
   // eslint-disable-next-line no-unused-vars
   const [consentData, setConsentData] = useState({
      summary: {
         activeConsents: 0,
         pendingReview: 0,
         withdrawn: 0,
         lastReviewed: new Date().toISOString()
      },
      consents: [],
      history: [],
      faq: [],
      // Contact details come from deployment configuration; nothing is shown when unset.
      privacyOfficer: {
         email: process.env.REACT_APP_PRIVACY_EMAIL || '',
         phone: process.env.REACT_APP_PRIVACY_PHONE || '',
         hours: process.env.REACT_APP_PRIVACY_HOURS || ''
      },
      legalLinks: {
         privacyNotice: 'https://www.hhs.gov/hipaa/for-individuals/index.html',
         privacyRights: 'https://www.hhs.gov/hipaa/for-individuals/guides/index.html',
         fileComplaint: 'https://www.hhs.gov/hipaa/filing-a-complaint/index.html'
      },
      lastPrivacyNoticeUpdate: new Date().toISOString()
   });

   // State
   const [expandedCards, setExpandedCards] = useState({});
   const [historyFilter, setHistoryFilter] = useState('all');
   const [showWithdrawModal, setShowWithdrawModal] = useState(false);
   const [showGrantModal, setShowGrantModal] = useState(false);
   const [showNewConsent, setShowNewConsent] = useState(false);
   const [selectedConsent, setSelectedConsent] = useState(null);
   const [showHelpSection, setShowHelpSection] = useState(false);
   const [expandedFaq, setExpandedFaq] = useState({});
   const [toast, setToast] = useState(null);

   // Form states for modals
   const [withdrawReason, setWithdrawReason] = useState('');
   const [withdrawEffective, setWithdrawEffective] = useState('immediately');
   const [withdrawConfirmed, setWithdrawConfirmed] = useState(false);
   const [consentMode, setConsentMode] = useState('grant');
   const [activeTab, setActiveTab] = useState('overview');
   const [dataSearchQuery, setDataSearchQuery] = useState('');
   const [isLoading, setIsLoading] = useState(true);
   const [fetchError, setFetchError] = useState(null);
   const [accessLog, setAccessLog] = useState(null); // null = not loaded yet
   const [accessLogError, setAccessLogError] = useState(false);

   // Consent type → display metadata mapping
   const consentTypeDisplayMap = {
      MEDICAL_RECORDS: { title: 'Medical Records Access', icon: 'BookOpen', description: 'Access to your full medical records and health history.' },
      PRESCRIPTIONS: { title: 'Prescription Data Access', icon: 'FlaskConical', description: 'Access to your current and past prescriptions.' },
      VITAL_SIGNS: { title: 'Vital Signs Access', icon: 'Stethoscope', description: 'Access to your recorded vital signs and measurements.' },
      LAB_RESULTS: { title: 'Lab Results Access', icon: 'FlaskConical', description: 'Access to your laboratory test results.' },
      ALL: { title: 'Full Health Data Access', icon: 'Users', description: 'Full access to all your health information and records.' },
   };

   const transformApiConsent = (c) => {
      const display = consentTypeDisplayMap[c.consentType] || {
         title: c.consentType.replace(/_/g, ' '),
         icon: 'Shield',
         description: `Health data access for ${c.consentType.replace(/_/g, ' ').toLowerCase()}.`,
      };
      return {
         id: c.id,
         title: display.title,
         icon: display.icon,
         description: c.reason || display.description,
         type: 'optional',
         status: c.status === 'ACTIVE' ? 'active' : 'withdrawn',
         canWithdraw: c.status === 'ACTIVE',
         grantedDate: c.grantedAt,
         lastModified: c.revokedAt || c.grantedAt,
         withdrawnDate: c.revokedAt || null,
         expiresAt: c.expiresAt,
         grantedTo: c.grantedTo,
         consentType: c.consentType,
      };
   };

   // Fetch consents from backend on mount
   useEffect(() => {
      const fetchConsents = async () => {
         try {
            setIsLoading(true);
            setFetchError(null);
            const apiConsents = await consentAPI.getMyConsents();
            const mapped = apiConsents.map(transformApiConsent);
            setConsentData(prev => ({
               ...prev,
               consents: mapped,
               summary: {
                  ...prev.summary,
                  activeConsents: mapped.filter(c => c.status === 'active').length,
                  pendingReview: 0,
                  withdrawn: mapped.filter(c => c.status === 'withdrawn').length,
                  lastReviewed: new Date().toISOString(),
               },
            }));
         } catch (err) {
            console.error('Failed to load consents:', err);
            setFetchError('Unable to load consent data. Please try again.');
         } finally {
            setIsLoading(false);
         }
      };
      fetchConsents();
   // eslint-disable-next-line react-hooks/exhaustive-deps
   }, []);

   // Load the real access log (who read my data) the first time the Data tab is opened
   useEffect(() => {
      if (activeTab !== 'data' || accessLog !== null) return undefined;
      let cancelled = false;
      consentAPI.getAccessLog()
         .then((rows) => { if (!cancelled) setAccessLog(Array.isArray(rows) ? rows : []); })
         .catch(() => { if (!cancelled) { setAccessLogError(true); setAccessLog([]); } });
      return () => { cancelled = true; };
   }, [activeTab, accessLog]);

   const accessLogView = (accessLog || [])
      .filter((l) => {
         const q = dataSearchQuery.trim().toLowerCase();
         return !q || (l.accessor || '').toLowerCase().includes(q) || (l.action || '').toLowerCase().includes(q);
      })
      .map((l) => ({ ...l, type: 'view' }));

   // Tab configuration
   const tabs = [
      { id: 'overview', label: 'Consent Overview', icon: Eye, description: 'View all your consent statuses' },
      { id: 'modify', label: 'Modify Consent', icon: Settings, description: 'Grant or modify your consents' },
      { id: 'data', label: 'Data Management', icon: Database, description: 'Manage your health data' }
   ];

   // Every consent here is a provider data-access grant, which is what the health-information-exchange
   // form describes (the old id map matched ids that real consents never have, so it always showed the research form).
   const getFormCategory = () => 'hie';

   // Icon mapping
   const iconMap = {
      Stethoscope, FlaskConical, Users, Network, Bell, Megaphone, BookOpen, Lock
   };

   // Toggle card expansion
   const toggleCard = (consentId) => {
      setExpandedCards(prev => ({
         ...prev,
         [consentId]: !prev[consentId]
      }));
   };

   // Toggle FAQ expansion
   const toggleFaq = (faqId) => {
      setExpandedFaq(prev => ({
         ...prev,
         [faqId]: !prev[faqId]
      }));
   };

   // Show toast notification
   const showToast = (type, message) => {
      setToast({ type, message });
      setTimeout(() => setToast(null), 4000);
   };

   // Format date helper
   const formatDate = (dateString) => {
      if (!dateString) return 'N/A';
      const date = new Date(dateString);
      return date.toLocaleDateString('en-US', {
         year: 'numeric',
         month: 'long',
         day: 'numeric'
      });
   };

   // Format date with time
   const formatDateTime = (dateString) => {
      if (!dateString) return 'N/A';
      const date = new Date(dateString);
      return date.toLocaleDateString('en-US', {
         year: 'numeric',
         month: 'long',
         day: 'numeric',
         hour: 'numeric',
         minute: '2-digit',
         hour12: true
      });
   };

   // Get relative time
   const getRelativeTime = (dateString) => {
      const date = new Date(dateString);
      const now = new Date();
      const diffMs = now - date;
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

      if (diffDays === 0) return 'Today';
      if (diffDays === 1) return 'Yesterday';
      if (diffDays < 7) return `${diffDays} days ago`;
      if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`;
      return `${Math.floor(diffDays / 30)} months ago`;
   };

   // Filter history based on selected filter
   const filteredHistory = useMemo(() => {
      if (historyFilter === 'all') return consentData.history;

      const now = new Date();
      let cutoff;

      switch (historyFilter) {
         case '30days':
            cutoff = new Date(now.setDate(now.getDate() - 30));
            break;
         case '6months':
            cutoff = new Date(now.setMonth(now.getMonth() - 6));
            break;
         case '1year':
            cutoff = new Date(now.setFullYear(now.getFullYear() - 1));
            break;
         default:
            return consentData.history;
      }

      return consentData.history.filter(h => new Date(h.timestamp) >= cutoff);
   }, [historyFilter, consentData.history]);

   // Handle withdraw consent
   const handleWithdrawClick = (consent) => {
      setSelectedConsent(consent);
      setWithdrawReason('');
      setWithdrawEffective('immediately');
      setWithdrawConfirmed(false);
      setShowWithdrawModal(true);
   };

   // Handle grant consent
   const handleGrantClick = (consent, mode = 'grant') => {
      setSelectedConsent(consent);
      setConsentMode(mode);
      setShowGrantModal(true);
   };

   // A brand-new consent was granted from the provider picker
   const handleNewConsentGranted = async (consent, provider) => {
      setShowNewConsent(false);
      showToast('success', `Access granted${provider ? ` to ${provider.email}` : ''}.`);
      await refreshConsents();
   };

   const refreshConsents = async () => {
      try {
         const apiConsents = await consentAPI.getMyConsents();
         const mapped = apiConsents.map(transformApiConsent);
         setConsentData(prev => ({
            ...prev,
            consents: mapped,
            summary: {
               ...prev.summary,
               activeConsents: mapped.filter(c => c.status === 'active').length,
               withdrawn: mapped.filter(c => c.status === 'withdrawn').length,
            },
         }));
      } catch (err) {
         console.error('Failed to refresh consents:', err);
      }
   };

   // Handle consent form submission
   const handleConsentSubmit = async (formData) => {
      const action = consentMode === 'grant' ? 'granted' : 'modified';
      showToast('success', `Consent ${action} for "${selectedConsent?.title}".`);
      setShowGrantModal(false);
      setSelectedConsent(null);
      // Refresh consent list from backend
      try {
         const apiConsents = await consentAPI.getMyConsents();
         const mapped = apiConsents.map(transformApiConsent);
         setConsentData(prev => ({
            ...prev,
            consents: mapped,
            summary: {
               ...prev.summary,
               activeConsents: mapped.filter(c => c.status === 'active').length,
               withdrawn: mapped.filter(c => c.status === 'withdrawn').length,
            },
         }));
      } catch (err) {
         console.error('Failed to refresh consents:', err);
      }
   };

   // Confirm withdrawal
   const confirmWithdraw = async () => {
      if (!withdrawConfirmed || !selectedConsent) return;

      try {
         await consentAPI.revokeConsent(selectedConsent.id, withdrawReason);
         // Update local state immediately so UI reflects revocation
         setConsentData(prev => {
            const updated = prev.consents.map(c =>
               c.id === selectedConsent.id
                  ? { ...c, status: 'withdrawn', canWithdraw: false, withdrawnDate: new Date().toISOString(), lastModified: new Date().toISOString() }
                  : c
            );
            return {
               ...prev,
               consents: updated,
               summary: {
                  ...prev.summary,
                  activeConsents: updated.filter(c => c.status === 'active').length,
                  withdrawn: updated.filter(c => c.status === 'withdrawn').length,
               },
            };
         });
         showToast('success', `Consent withdrawn for "${selectedConsent.title}".`);
      } catch (err) {
         console.error('Revoke consent failed:', err);
         showToast('error', 'Failed to withdraw consent. Please try again.');
      }
      setShowWithdrawModal(false);
      setSelectedConsent(null);
   };

   // Get status styling
   const getStatusStyles = (status, type) => {
      switch (status) {
         case 'active':
         case 'granted':
            return {
               border: 'border-l-4 border-l-green-500',
               badge: 'bg-green-100 text-green-800',
               icon: <CheckCircle className="w-3.5 h-3.5 text-green-600" />,
               text: type === 'required' ? 'Active (Required)' : 'Active'
            };
         case 'withdrawn':
            return {
               border: 'border-l-4 border-l-gray-400',
               badge: 'bg-gray-100 text-gray-700',
               icon: <XCircle className="w-3.5 h-3.5 text-gray-500" />,
               text: 'Withdrawn'
            };
         case 'pending':
            return {
               border: 'border-l-4 border-l-orange-500',
               badge: 'bg-orange-100 text-orange-800',
               icon: <Clock className="w-3.5 h-3.5 text-orange-600 animate-pulse" />,
               text: 'Pending'
            };
         default:
            return {
               border: 'border-l-4 border-l-blue-500',
               badge: 'bg-blue-100 text-blue-800',
               icon: <Shield className="w-3.5 h-3.5 text-blue-600" />,
               text: 'Active'
            };
      }
   };

   // Get type badge
   const getTypeBadge = (type) => {
      switch (type) {
         case 'required':
            return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300">REQUIRED</span>;
         case 'optional':
            return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">OPTIONAL</span>;
         case 'special':
            return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-purple-100 dark:bg-purple-900/30 text-purple-800 dark:text-purple-300">SPECIAL</span>;
         default:
            return null;
      }
   };

   // Get action badge for history
   const getActionBadge = (action) => {
      switch (action) {
         case 'granted':
            return <Badge type="green">Granted</Badge>;
         case 'withdrawn':
            return <Badge type="red">Withdrawn</Badge>;
         case 'modified':
            return <Badge type="yellow">Modified</Badge>;
         default:
            return <Badge>{action}</Badge>;
      }
   };

   // Render consent card
   const renderConsentCard = (consent) => {
      const IconComponent = iconMap[consent.icon] || Shield;
      const statusStyles = getStatusStyles(consent.status, consent.type);
      const isExpanded = expandedCards[consent.id];

      return (
         <Card
            key={consent.id}
            className={`${statusStyles.border} overflow-hidden transition-all duration-300 hover:shadow-lg`}
         >
            {/* Card Header */}
            <div
               className="p-3 cursor-pointer"
               onClick={() => toggleCard(consent.id)}
            >
               <div className="flex items-start justify-between">
                  <div className="flex items-start gap-2">
                     <div className={`w-8 h-8 rounded flex items-center justify-center ${consent.status === 'granted' || consent.status === 'active' ? 'bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400' :
                        consent.status === 'withdrawn' ? 'bg-gray-100 dark:bg-slate-700 text-gray-500 dark:text-slate-400' :
                           consent.status === 'pending' ? 'bg-orange-50 dark:bg-orange-900/20 text-orange-600 dark:text-orange-400' :
                              'bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400'
                        }`}>
                        <IconComponent className="w-4 h-4" />
                     </div>
                     <div className="flex-1">
                        <div className="flex items-center gap-1.5 flex-wrap">
                           <h3 className="font-bold text-gray-900 dark:text-slate-100 text-sm">{consent.title}</h3>
                           {getTypeBadge(consent.type)}
                        </div>
                        <div className="flex items-center gap-1.5 mt-1">
                           {statusStyles.icon}
                           <span className={`text-xs font-medium ${consent.status === 'granted' || consent.status === 'active' ? 'text-green-700 dark:text-green-400' :
                              consent.status === 'withdrawn' ? 'text-gray-600 dark:text-slate-300' :
                                 consent.status === 'pending' ? 'text-orange-700 dark:text-orange-400' :
                                    'text-blue-700 dark:text-blue-400'
                              }`}>
                              {statusStyles.text}
                           </span>
                        </div>
                     </div>
                  </div>
                  <div className="flex items-center gap-1.5">
                     {consent.status === 'pending' && (
                        <span className="px-1.5 py-0.5 bg-orange-100 dark:bg-orange-900/30 text-orange-800 dark:text-orange-300 rounded-full text-xs font-semibold animate-pulse">
                           Action
                        </span>
                     )}
                     {isExpanded ? (
                        <ChevronUp className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                     ) : (
                        <ChevronDown className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                     )}
                  </div>
               </div>
            </div>

            {/* Expanded Content */}
            {isExpanded && (
               <div className="px-3 pb-3 pt-1.5 border-t border-gray-100 dark:border-slate-700 animate-fade-in">
                  {/* Description */}
                  <p className="text-gray-600 dark:text-slate-300 mb-2 text-xs">{consent.description}</p>

                  {/* What this includes */}
                  {consent.includes && (
                     <div className="mb-2">
                        <h4 className="text-xs font-semibold text-gray-700 dark:text-slate-200 mb-1">What this includes:</h4>
                        <ul className="space-y-0.5">
                           {consent.includes.map((item, idx) => (
                              <li key={idx} className="flex items-start gap-1.5 text-xs text-gray-600 dark:text-slate-300">
                                 <Check className="w-3 h-3 text-green-500 dark:text-green-400 flex-shrink-0 mt-0.5" />
                                 {item}
                              </li>
                           ))}
                        </ul>
                     </div>
                  )}

                  {/* Benefits (for HIE) */}
                  {consent.benefits && (
                     <div className="mb-2">
                        <h4 className="text-xs font-semibold text-gray-700 dark:text-slate-200 mb-1">Benefits:</h4>
                        <ul className="space-y-0.5">
                           {consent.benefits.map((item, idx) => (
                              <li key={idx} className="flex items-start gap-1.5 text-xs text-gray-600 dark:text-slate-300">
                                 <Check className="w-3 h-3 text-blue-500 dark:text-blue-400 flex-shrink-0 mt-0.5" />
                                 {item}
                              </li>
                           ))}
                        </ul>
                     </div>
                  )}

                  {/* What is shared (for HIE) */}
                  {consent.whatIsShared && (
                     <div className="mb-2">
                        <h4 className="text-xs font-semibold text-gray-700 dark:text-slate-200 mb-1">Shared info:</h4>
                        <ul className="space-y-0.5">
                           {consent.whatIsShared.map((item, idx) => (
                              <li key={idx} className="flex items-start gap-1.5 text-xs text-gray-600 dark:text-slate-300">
                                 <FileText className="w-3 h-3 text-gray-400 dark:text-slate-500 flex-shrink-0 mt-0.5" />
                                 {item}
                              </li>
                           ))}
                        </ul>
                     </div>
                  )}

                  {/* Who can access */}
                  {consent.whoCanAccess && (
                     <div className="mb-2">
                        <h4 className="text-xs font-semibold text-gray-700 dark:text-slate-200 mb-1">Who can access:</h4>
                        <ul className="space-y-0.5">
                           {consent.whoCanAccess.map((item, idx) => (
                              <li key={idx} className="flex items-start gap-1.5 text-xs text-gray-600 dark:text-slate-300">
                                 <User className="w-3 h-3 text-gray-400 dark:text-slate-500 flex-shrink-0 mt-0.5" />
                                 {item}
                              </li>
                           ))}
                        </ul>
                     </div>
                  )}

                  {/* Privacy notes */}
                  {consent.privacyNotes && (
                     <div className="mb-2 bg-blue-50 dark:bg-blue-900/20 rounded p-2">
                        <h4 className="text-xs font-semibold text-blue-800 dark:text-blue-300 mb-1 flex items-center gap-1">
                           <Info className="w-3 h-3" />
                           Important
                        </h4>
                        <ul className="space-y-0.5">
                           {consent.privacyNotes.map((note, idx) => (
                              <li key={idx} className="text-xs text-blue-700 dark:text-blue-400">• {note}</li>
                           ))}
                        </ul>
                     </div>
                  )}

                  {/* Authorized Persons (for family sharing) */}
                  {consent.authorizedPersons && (
                     <div className="mb-2">
                        <h4 className="text-xs font-semibold text-gray-700 dark:text-slate-200 mb-1.5">Authorized Individuals:</h4>
                        <div className="space-y-1.5">
                           {consent.authorizedPersons.map((person) => (
                              <div key={person.id} className="flex items-center justify-between bg-gray-50 dark:bg-slate-800/50 rounded p-2">
                                 <div className="flex items-center gap-2">
                                    <div className="w-7 h-7 bg-blue-100 dark:bg-blue-900/30 rounded-full flex items-center justify-center">
                                       <User className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
                                    </div>
                                    <div>
                                       <p className="font-medium text-gray-900 dark:text-slate-100 text-xs">{person.name} ({person.relationship})</p>
                                       <p className="text-xs text-gray-500 dark:text-slate-400">{person.phone}</p>
                                    </div>
                                 </div>
                                 <div className="flex items-center gap-1">
                                    <Button size="sm" variant="ghost" className="text-gray-500 dark:text-slate-400 hover:text-gray-700 dark:hover:text-slate-200 p-1">
                                       <Edit3 className="w-3 h-3" />
                                    </Button>
                                    <Button size="sm" variant="ghost" className="text-red-500 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300 p-1">
                                       <Trash2 className="w-3 h-3" />
                                    </Button>
                                 </div>
                              </div>
                           ))}
                           <Button variant="outline" size="sm" className="w-full mt-1.5 text-xs py-1">
                              <Plus className="w-3 h-3 mr-1" />
                              Add Person
                           </Button>
                        </div>
                     </div>
                  )}

                  {/* Communication Preferences */}
                  {consent.preferences && (
                     <div className="mb-4">
                        <h4 className="text-sm font-semibold text-gray-700 dark:text-slate-200 mb-3">Communication Channels:</h4>
                        <div className="space-y-2">
                           <div className="flex items-center justify-between py-2 border-b border-gray-100 dark:border-slate-700">
                              <div className="flex items-center gap-2">
                                 <Mail className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                                 <span className="text-sm text-gray-600 dark:text-slate-300">Email: {consent.preferences.email.address}</span>
                              </div>
                              {consent.preferences.email.enabled ? (
                                 <span className="text-green-600 dark:text-green-400 text-sm flex items-center gap-1">
                                    <Check className="w-4 h-4" /> Enabled
                                 </span>
                              ) : (
                                 <span className="text-gray-500 dark:text-slate-400 text-sm flex items-center gap-1">
                                    <X className="w-4 h-4" /> Disabled
                                 </span>
                              )}
                           </div>
                           <div className="flex items-center justify-between py-2 border-b border-gray-100 dark:border-slate-700">
                              <div className="flex items-center gap-2">
                                 <MessageSquare className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                                 <span className="text-sm text-gray-600 dark:text-slate-300">SMS/Text: {consent.preferences.sms.phone}</span>
                              </div>
                              {consent.preferences.sms.enabled ? (
                                 <span className="text-green-600 dark:text-green-400 text-sm flex items-center gap-1">
                                    <Check className="w-4 h-4" /> Enabled
                                 </span>
                              ) : (
                                 <span className="text-gray-500 dark:text-slate-400 text-sm flex items-center gap-1">
                                    <X className="w-4 h-4" /> Disabled
                                 </span>
                              )}
                           </div>
                           <div className="flex items-center justify-between py-2 border-b border-gray-100 dark:border-slate-700">
                              <div className="flex items-center gap-2">
                                 <Phone className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                                 <span className="text-sm text-gray-600 dark:text-slate-300">Phone calls: {consent.preferences.phone.phone}</span>
                              </div>
                              {consent.preferences.phone.enabled ? (
                                 <span className="text-green-600 dark:text-green-400 text-sm flex items-center gap-1">
                                    <Check className="w-4 h-4" /> Enabled
                                 </span>
                              ) : (
                                 <span className="text-gray-500 dark:text-slate-400 text-sm flex items-center gap-1">
                                    <X className="w-4 h-4" /> Disabled
                                 </span>
                              )}
                           </div>
                           <div className="flex items-center justify-between py-2">
                              <div className="flex items-center gap-2">
                                 <Bell className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                                 <span className="text-sm text-gray-600 dark:text-slate-300">Portal Notifications</span>
                              </div>
                              {consent.preferences.portal.enabled ? (
                                 <span className="text-green-600 dark:text-green-400 text-sm flex items-center gap-1">
                                    <Check className="w-4 h-4" /> Enabled
                                 </span>
                              ) : (
                                 <span className="text-gray-500 dark:text-slate-400 text-sm flex items-center gap-1">
                                    <X className="w-4 h-4" /> Disabled
                                 </span>
                              )}
                           </div>
                        </div>
                     </div>
                  )}

                  {/* Active Restrictions (for sensitive info) */}
                  {consent.activeRestrictions && consent.activeRestrictions.length > 0 && (
                     <div className="mb-4">
                        <h4 className="text-sm font-semibold text-gray-700 dark:text-slate-200 mb-3">Active Restrictions:</h4>
                        <div className="space-y-2">
                           {consent.activeRestrictions.map((restriction) => (
                              <div key={restriction.id} className="bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800 rounded-lg p-3">
                                 <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-2">
                                       <Lock className="w-4 h-4 text-orange-600 dark:text-orange-400" />
                                       <span className="font-medium text-orange-800 dark:text-orange-300">{restriction.label}</span>
                                    </div>
                                    <Badge type="yellow">Restricted</Badge>
                                 </div>
                                 <p className="text-sm text-orange-700 dark:text-orange-400 mt-1">{restriction.restriction}</p>
                                 <p className="text-xs text-orange-600 dark:text-orange-500 mt-1">Effective: {formatDate(restriction.effectiveDate)}</p>
                              </div>
                           ))}
                        </div>
                     </div>
                  )}

                  {/* Important note */}
                  {consent.importantNote && (
                     <div className="mb-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-3">
                        <div className="flex items-start gap-2">
                           <AlertTriangle className="w-4 h-4 text-yellow-600 dark:text-yellow-400 flex-shrink-0 mt-0.5" />
                           <p className="text-sm text-yellow-800 dark:text-yellow-300">{consent.importantNote}</p>
                        </div>
                     </div>
                  )}

                  {/* Information for directory listing */}
                  {consent.informationIncluded && (
                     <div className="mb-4">
                        <h4 className="text-sm font-semibold text-gray-700 dark:text-slate-200 mb-2">Information included if you consent:</h4>
                        <ul className="space-y-1">
                           {consent.informationIncluded.map((item, idx) => (
                              <li key={idx} className="flex items-start gap-2 text-sm text-gray-600 dark:text-slate-300">
                                 <Check className="w-4 h-4 text-gray-400 dark:text-slate-500 flex-shrink-0 mt-0.5" />
                                 {item}
                              </li>
                           ))}
                        </ul>
                     </div>
                  )}

                  {/* Dates */}
                  <div className="mt-4 pt-4 border-t border-gray-100 dark:border-slate-700">
                     <div className="flex flex-wrap gap-4 text-sm text-gray-500 dark:text-slate-400">
                        {consent.grantedDate && (
                           <div className="flex items-center gap-1">
                              <Calendar className="w-4 h-4" />
                              <span>Granted: {formatDate(consent.grantedDate)}</span>
                           </div>
                        )}
                        {consent.withdrawnDate && (
                           <div className="flex items-center gap-1">
                              <Calendar className="w-4 h-4" />
                              <span>Withdrawn: {formatDate(consent.withdrawnDate)}</span>
                           </div>
                        )}
                        {consent.lastModified && (
                           <div className="flex items-center gap-1">
                              <Clock className="w-4 h-4" />
                              <span>Last modified: {getRelativeTime(consent.lastModified)}</span>
                           </div>
                        )}
                     </div>
                  </div>

                  {/* Action Buttons */}
                  {consent.type !== 'required' && (
                     <div className="mt-4 pt-4 border-t border-gray-100 dark:border-slate-700 flex flex-wrap gap-3">
                        {consent.status === 'pending' && (
                           <>
                              <Button
                                 className="bg-brand-medium hover:bg-brand-deep"
                                 onClick={() => handleGrantClick(consent)}
                              >
                                 <Check className="w-4 h-4 mr-2" />
                                 Grant Consent
                              </Button>
                              <Button variant="outline" className="text-gray-600 dark:text-slate-300">
                                 Decline
                              </Button>
                              <Button variant="ghost" className="text-gray-500 dark:text-slate-400">
                                 Ask Me Later
                              </Button>
                           </>
                        )}
                        {(consent.status === 'granted' || consent.status === 'active') && consent.canWithdraw && (
                           <>
                              {consent.preferences && (
                                 <Button variant="outline">
                                    <Edit3 className="w-4 h-4 mr-2" />
                                    Update Preferences
                                 </Button>
                              )}
                              {consent.authorizedPersons && (
                                 <Button variant="outline">
                                    <Users className="w-4 h-4 mr-2" />
                                    Modify Authorized List
                                 </Button>
                              )}
                              {consent.category === 'hie' && (
                                 <Button variant="outline">
                                    <Edit3 className="w-4 h-4 mr-2" />
                                    Manage Sharing Preferences
                                 </Button>
                              )}
                              {consent.activeRestrictions && (
                                 <>
                                    <Button variant="outline">
                                       <Plus className="w-4 h-4 mr-2" />
                                       Add Restriction
                                    </Button>
                                    <Button variant="outline">
                                       <Edit3 className="w-4 h-4 mr-2" />
                                       Modify Restrictions
                                    </Button>
                                 </>
                              )}
                              <Button
                                 variant="outline"
                                 className="text-red-600 dark:text-red-400 border-red-300 dark:border-red-700 hover:bg-red-50 dark:hover:bg-red-900/20"
                                 onClick={() => handleWithdrawClick(consent)}
                              >
                                 <XCircle className="w-4 h-4 mr-2" />
                                 Withdraw Consent
                              </Button>
                           </>
                        )}
                        {consent.status === 'withdrawn' && (
                           <Button
                              className="bg-brand-medium hover:bg-brand-deep"
                              onClick={() => handleGrantClick(consent)}
                           >
                              <Check className="w-4 h-4 mr-2" />
                              Grant Consent
                           </Button>
                        )}
                     </div>
                  )}

                  {/* Required consent notice */}
                  {consent.type === 'required' && (
                     <div className="mt-4 pt-4 border-t border-gray-100 dark:border-slate-700">
                        <div className="flex items-center gap-2 text-blue-600 dark:text-blue-400 text-sm">
                           <Lock className="w-4 h-4" />
                           <span>This consent is required for treatment and cannot be withdrawn.</span>
                        </div>
                     </div>
                  )}
               </div>
            )}
         </Card>
      );
   };

   return (
      <div className="space-y-8">
         {/* Toast Notification */}
         {toast && (
            <div className={`fixed top-4 right-4 z-50 p-4 rounded-xl shadow-lg animate-fade-in flex items-center gap-3 ${toast.type === 'success' ? 'bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 text-green-800 dark:text-green-300' :
               toast.type === 'error' ? 'bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-300' :
                  'bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 text-blue-800 dark:text-blue-300'
               }`}>
               {toast.type === 'success' && <CheckCircle className="w-5 h-5 text-green-600 dark:text-green-400" />}
               {toast.type === 'error' && <XCircle className="w-5 h-5 text-red-600 dark:text-red-400" />}
               <span className="font-medium">{toast.message}</span>
               <button onClick={() => setToast(null)} className="ml-2 hover:opacity-70">
                  <X className="w-4 h-4" />
               </button>
            </div>
         )}

         {/* Page Header */}
         <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-2">
            <div>
               <div className="flex items-center gap-2">
                  <div className="w-8 h-8 bg-blue-100 dark:bg-blue-900/30 rounded flex items-center justify-center">
                     <Shield className="w-4 h-4 text-blue-600 dark:text-blue-400" />
                  </div>
                  <div>
                     <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100 tracking-tight">Privacy & Consent</h2>
                     <p className="text-xs text-gray-500 dark:text-slate-400">Manage how your health information is used</p>
                  </div>
               </div>
            </div>
            <div className="flex gap-2">
               <IconOnlyButton 
                  icon={Download} 
                  tooltip="Download Consent History" 
                  variant="secondary"
               />
            </div>
         </div>

         {/* Tab Navigation */}
         <div className="border-b border-gray-200 dark:border-slate-700">
            <nav className="-mb-px flex space-x-1 md:space-x-4 overflow-x-auto pb-px" aria-label="Tabs">
               {tabs.map((tab) => {
                  const IconComponent = tab.icon;
                  const isActive = activeTab === tab.id;
                  return (
                     <button
                        key={tab.id}
                        onClick={() => setActiveTab(tab.id)}
                        className={`group inline-flex items-center gap-1.5 px-2 py-2 border-b-2 font-medium text-xs whitespace-nowrap transition-all duration-200 ${isActive
                           ? 'border-blue-600 dark:border-blue-400 text-blue-600 dark:text-blue-400'
                           : 'border-transparent text-gray-500 dark:text-slate-400 hover:text-gray-700 dark:hover:text-slate-200 hover:border-gray-300 dark:hover:border-slate-600'
                           }`}
                        aria-current={isActive ? 'page' : undefined}
                     >
                        <IconComponent className={`w-3.5 h-3.5 ${isActive ? 'text-blue-600 dark:text-blue-400' : 'text-gray-400 dark:text-slate-500 group-hover:text-gray-500 dark:group-hover:text-slate-400'}`} />
                        <span>{tab.label}</span>
                     </button>
                  );
               })}
            </nav>
         </div>

         {/* Tab Content */}
         {isLoading ? (
            <div className="flex items-center justify-center py-16">
               <RefreshCw className="w-6 h-6 animate-spin text-blue-500 mr-2" />
               <span className="text-gray-500 dark:text-slate-400">Loading your consent data...</span>
            </div>
         ) : fetchError ? (
            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-4 flex items-center gap-3">
               <AlertTriangle className="w-5 h-5 text-red-600 dark:text-red-400 flex-shrink-0" />
               <span className="text-red-700 dark:text-red-300 text-sm">{fetchError}</span>
            </div>
         ) : null}
         {!isLoading && !fetchError && activeTab === 'overview' && (
            <>
               {/* Important Notice Banner */}
               <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-4 flex items-start gap-3">
                  <Info className="w-5 h-5 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
                  <div className="flex-1">
                     <h3 className="font-semibold text-blue-900 dark:text-blue-300">Your Healthcare Privacy Rights Under HIPAA</h3>
                     <p className="text-blue-700 dark:text-blue-400 text-sm mt-1">
                        You have the right to control how your protected health information is used and shared.
                        Changes may take 24-48 hours to process.
                     </p>
                  </div>
                  <div className="text-sm text-blue-600 dark:text-blue-400 flex items-center gap-1">
                     <Clock className="w-4 h-4" />
                     <span>Last reviewed: {formatDate(consentData.summary.lastReviewed)}</span>
                  </div>
               </div>

               {/* Summary Cards */}
               <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                  <Card className="p-3 border border-gray-100 dark:border-slate-700 shadow-soft hover:shadow-md transition-shadow duration-300 flex items-center justify-between group">
                     <div>
                        <h3 className="text-gray-500 dark:text-slate-400 text-xs font-medium">Active Consents</h3>
                        <p className="text-xl font-bold text-gray-800 dark:text-slate-100 mt-1 group-hover:text-green-600 dark:group-hover:text-green-400 transition-colors">
                           {consentData.summary.activeConsents}
                        </p>
                        <p className="text-xs text-gray-500 dark:text-slate-400">active consents</p>
                     </div>
                     <div className="w-8 h-8 bg-green-50 dark:bg-green-900/20 rounded-full flex items-center justify-center text-green-600 dark:text-green-400 group-hover:scale-110 transition-transform">
                        <CheckCircle className="w-4 h-4" />
                     </div>
                  </Card>

                  <Card className="p-3 border border-gray-100 dark:border-slate-700 shadow-soft hover:shadow-md transition-shadow duration-300 flex items-center justify-between group">
                     <div>
                        <h3 className="text-gray-500 dark:text-slate-400 text-xs font-medium">Pending Review</h3>
                        <p className="text-xl font-bold text-gray-800 dark:text-slate-100 mt-1 group-hover:text-orange-600 dark:group-hover:text-orange-400 transition-colors">
                           {consentData.summary.pendingReview}
                        </p>
                        <p className="text-xs text-gray-500 dark:text-slate-400">pending review</p>
                     </div>
                     <div className="w-8 h-8 bg-orange-50 dark:bg-orange-900/20 rounded-full flex items-center justify-center text-orange-600 dark:text-orange-400 group-hover:scale-110 transition-transform">
                        <Clock className="w-4 h-4" />
                     </div>
                  </Card>

                  <Card className="p-3 border border-gray-100 dark:border-slate-700 shadow-soft hover:shadow-md transition-shadow duration-300 flex items-center justify-between group">
                     <div>
                        <h3 className="text-gray-500 dark:text-slate-400 text-xs font-medium">Withdrawn</h3>
                        <p className="text-xl font-bold text-gray-800 dark:text-slate-100 mt-1 group-hover:text-gray-600 dark:group-hover:text-slate-300 transition-colors">
                           {consentData.summary.withdrawn}
                        </p>
                        <p className="text-xs text-gray-500 dark:text-slate-400">withdrawn</p>
                     </div>
                     <div className="w-8 h-8 bg-gray-100 dark:bg-slate-700 rounded-full flex items-center justify-center text-gray-500 dark:text-slate-400 group-hover:scale-110 transition-transform">
                        <XCircle className="w-4 h-4" />
                     </div>
                  </Card>
               </div>

               {/* Consent Categories Section */}
               <div>
                  <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-2 flex items-center gap-1.5">
                     <FileText className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                     Consent Categories
                  </h3>
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
                     {consentData.consents.map(consent => renderConsentCard(consent))}
                  </div>
               </div>

               {/* Consent History Timeline */}
               <Card className="overflow-hidden border border-gray-100 dark:border-slate-700 shadow-soft">
                  <div className="px-3 py-2.5 border-b border-gray-100 dark:border-slate-700 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2 bg-white dark:bg-slate-800">
                     <h3 className="text-sm font-bold text-gray-800 dark:text-slate-100 flex items-center gap-1.5">
                        <History className="w-4 h-4 text-gray-400 dark:text-slate-500" />
                        Consent History
                     </h3>
                     <div className="flex items-center gap-2">
                        <select
                           value={historyFilter}
                           onChange={(e) => setHistoryFilter(e.target.value)}
                           className="text-xs border border-gray-300 dark:border-slate-600 rounded px-2 py-1 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                        >
                           <option value="all">All changes</option>
                           <option value="30days">Last 30 days</option>
                           <option value="6months">Last 6 months</option>
                           <option value="1year">Last year</option>
                        </select>
                        <IconOnlyButton 
                           icon={Download} 
                           tooltip="Export History" 
                           variant="secondary"
                           size="sm"
                        />
                     </div>
                  </div>
                  <div className="p-6">
                     <div className="relative">
                        {/* Timeline line */}
                        <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-gray-200 dark:bg-slate-700" />

                        {/* Timeline items */}
                        <div className="space-y-6">
                           {filteredHistory.map((item, idx) => (
                              <div key={item.id} className="relative pl-10">
                                 {/* Timeline dot */}
                                 <div className={`absolute left-2 w-5 h-5 rounded-full flex items-center justify-center ${item.action === 'granted' ? 'bg-green-100 dark:bg-green-900/30' :
                                    item.action === 'withdrawn' ? 'bg-red-100 dark:bg-red-900/30' :
                                       'bg-yellow-100 dark:bg-yellow-900/30'
                                    }`}>
                                    {item.action === 'granted' && <Check className="w-3 h-3 text-green-600 dark:text-green-400" />}
                                    {item.action === 'withdrawn' && <X className="w-3 h-3 text-red-600 dark:text-red-400" />}
                                    {item.action === 'modified' && <Edit3 className="w-3 h-3 text-yellow-600 dark:text-yellow-400" />}
                                 </div>

                                 {/* Content */}
                                 <div className="bg-gray-50 dark:bg-slate-800/50 rounded-lg p-4">
                                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                                       <div className="flex items-center gap-2">
                                          {getActionBadge(item.action)}
                                          <span className="font-medium text-gray-900 dark:text-slate-100">{item.categoryTitle}</span>
                                       </div>
                                       <span className="text-sm text-gray-500 dark:text-slate-400">{formatDateTime(item.timestamp)}</span>
                                    </div>
                                    <p className="text-sm text-gray-600 dark:text-slate-300 mt-2">{item.details}</p>
                                    <p className="text-xs text-gray-400 dark:text-slate-500 mt-1">Changed by: {item.changedByName}</p>
                                 </div>
                              </div>
                           ))}
                        </div>
                     </div>
                  </div>
               </Card>

               {/* Help & Information Section */}
               <Card className="overflow-hidden border border-gray-100 dark:border-slate-700 shadow-soft">
                  <div
                     className="px-6 py-5 border-b border-gray-100 dark:border-slate-700 flex justify-between items-center cursor-pointer hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors"
                     onClick={() => setShowHelpSection(!showHelpSection)}
                  >
                     <h3 className="text-lg font-bold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <HelpCircle className="w-5 h-5 text-gray-400 dark:text-slate-500" />
                        Understanding Your Privacy Rights
                     </h3>
                     {showHelpSection ? (
                        <ChevronUp className="w-5 h-5 text-gray-400 dark:text-slate-500" />
                     ) : (
                        <ChevronDown className="w-5 h-5 text-gray-400 dark:text-slate-500" />
                     )}
                  </div>

                  {showHelpSection && (
                     <div className="p-6 animate-fade-in">
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                           {/* What is HIPAA */}
                           <div>
                              <h4 className="font-semibold text-gray-800 dark:text-slate-100 mb-3">What is HIPAA?</h4>
                              <p className="text-sm text-gray-600 dark:text-slate-300">
                                 HIPAA (Health Insurance Portability and Accountability Act) is a federal law that
                                 protects the privacy and security of your health information. It gives you rights
                                 over your health information and sets limits on who can access it.
                              </p>
                           </div>

                           {/* Your Rights */}
                           <div>
                              <h4 className="font-semibold text-gray-800 dark:text-slate-100 mb-3">Your Rights Under HIPAA</h4>
                              <ul className="space-y-2 text-sm text-gray-600 dark:text-slate-300">
                                 <li className="flex items-start gap-2">
                                    <Check className="w-4 h-4 text-green-500 dark:text-green-400 flex-shrink-0 mt-0.5" />
                                    Right to access your medical records
                                 </li>
                                 <li className="flex items-start gap-2">
                                    <Check className="w-4 h-4 text-green-500 dark:text-green-400 flex-shrink-0 mt-0.5" />
                                    Right to request corrections to your records
                                 </li>
                                 <li className="flex items-start gap-2">
                                    <Check className="w-4 h-4 text-green-500 dark:text-green-400 flex-shrink-0 mt-0.5" />
                                    Right to know who has accessed your information
                                 </li>
                                 <li className="flex items-start gap-2">
                                    <Check className="w-4 h-4 text-green-500 dark:text-green-400 flex-shrink-0 mt-0.5" />
                                    Right to request restrictions on use/disclosure
                                 </li>
                                 <li className="flex items-start gap-2">
                                    <Check className="w-4 h-4 text-green-500 dark:text-green-400 flex-shrink-0 mt-0.5" />
                                    Right to file a complaint if you believe your rights were violated
                                 </li>
                              </ul>
                           </div>
                        </div>

                        {/* FAQ */}
                        <div className="mt-8">
                           <h4 className="font-semibold text-gray-800 dark:text-slate-100 mb-4">Frequently Asked Questions</h4>
                           <div className="space-y-3">
                              {consentData.faq.map((faq) => (
                                 <div key={faq.id} className="border border-gray-200 dark:border-slate-700 rounded-lg overflow-hidden">
                                    <button
                                       className="w-full px-4 py-3 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors"
                                       onClick={() => toggleFaq(faq.id)}
                                    >
                                       <span className="font-medium text-gray-800 dark:text-slate-100">{faq.question}</span>
                                       {expandedFaq[faq.id] ? (
                                          <ChevronUp className="w-5 h-5 text-gray-400 dark:text-slate-500" />
                                       ) : (
                                          <ChevronDown className="w-5 h-5 text-gray-400 dark:text-slate-500" />
                                       )}
                                    </button>
                                    {expandedFaq[faq.id] && (
                                       <div className="px-4 pb-3 pt-1 text-sm text-gray-600 dark:text-slate-300 animate-fade-in">
                                          {faq.answer}
                                       </div>
                                    )}
                                 </div>
                              ))}
                           </div>
                        </div>

                        {/* Contact Information */}
                        {(consentData.privacyOfficer.email || consentData.privacyOfficer.phone) && (
                        <div className="mt-8 bg-blue-50 dark:bg-blue-900/20 rounded-xl p-6">
                           <h4 className="font-semibold text-blue-900 dark:text-blue-300 mb-3">Contact Our Privacy Officer</h4>
                           <div className="flex flex-wrap gap-6 text-sm">
{consentData.privacyOfficer.email && (
                              <div className="flex items-center gap-2 text-blue-700 dark:text-blue-400">
                                 <Mail className="w-4 h-4" />
                                 <span>{consentData.privacyOfficer.email}</span>
                              </div>
)}
{consentData.privacyOfficer.phone && (
                              <div className="flex items-center gap-2 text-blue-700 dark:text-blue-400">
                                 <Phone className="w-4 h-4" />
                                 <span>{consentData.privacyOfficer.phone}</span>
                              </div>
)}
{consentData.privacyOfficer.hours && (
                              <div className="flex items-center gap-2 text-blue-700 dark:text-blue-400">
                                 <Clock className="w-4 h-4" />
                                 <span>{consentData.privacyOfficer.hours}</span>
                              </div>
)}
                           </div>
                        </div>
                        )}
                     </div>
                  )}
               </Card>

               {/* Legal Notices Footer */}
               <div className="bg-gray-50 dark:bg-slate-800/50 rounded-xl p-6 border border-gray-200 dark:border-slate-700">
                  <div className="flex flex-wrap items-center justify-between gap-4">
                     <div className="flex flex-wrap gap-4">
                        <a href={consentData.legalLinks.privacyNotice} className="text-sm text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 flex items-center gap-1">
                           <FileText className="w-4 h-4" />
                           Notice of Privacy Practices
                           <ExternalLink className="w-3 h-3" />
                        </a>
                        <a href={consentData.legalLinks.privacyRights} className="text-sm text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 flex items-center gap-1">
                           <Shield className="w-4 h-4" />
                           Your Privacy Rights
                           <ExternalLink className="w-3 h-3" />
                        </a>
                        <a href={consentData.legalLinks.fileComplaint} className="text-sm text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 flex items-center gap-1">
                           <AlertTriangle className="w-4 h-4" />
                           File a Privacy Complaint
                           <ExternalLink className="w-3 h-3" />
                        </a>
                     </div>
                     <p className="text-xs text-gray-500 dark:text-slate-400">
                        Privacy Notice last updated: {formatDate(consentData.lastPrivacyNoticeUpdate)}
                     </p>
                  </div>
                  <div className="mt-4 text-xs text-gray-500 dark:text-slate-400">
                     <p className="font-medium mb-1">Important Legal Notice:</p>
                     <p>
                        Some uses and disclosures of your health information are required by law and do not require your
                        authorization, including: public health reporting, legal proceedings, law enforcement, and other
                        activities as permitted by HIPAA.
                     </p>
                  </div>
               </div>
            </>
         )}

         {/* Modify Consent Tab */}
         {!isLoading && !fetchError && activeTab === 'modify' && (
            <>
               {/* Modify Consent Header */}
               <div className="bg-gradient-to-r from-blue-50 to-green-50 dark:from-blue-900/20 dark:to-green-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-6">
                  <div className="flex items-start gap-4">
                     <div className="w-12 h-12 bg-blue-100 dark:bg-blue-900/30 rounded-xl flex items-center justify-center">
                        <Settings className="w-6 h-6 text-blue-600 dark:text-blue-400" />
                     </div>
                     <div className="flex-1">
                        <h3 className="text-lg font-bold text-gray-800 dark:text-slate-100">Grant or Modify Your Consents</h3>
                        <p className="text-gray-600 dark:text-slate-300 mt-1">
                           Select any consent category below to grant new consent or modify existing preferences.
                           Each consent can be tailored with granular options for your comfort level.
                        </p>
                     </div>
                  </div>
               </div>

               {/* Consent Categories Grid */}
               <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  {consentData.consents.map(consent => {
                     const IconComponent = iconMap[consent.icon];
                     const statusStyles = getStatusStyles(consent.status, consent.type);
                     const isGranted = consent.status === 'active' || consent.status === 'granted';

                     return (
                        <Card
                           key={consent.id}
                           className={`p-6 border ${statusStyles.border} shadow-soft hover:shadow-lg transition-all duration-300`}
                        >
                           <div className="flex items-start justify-between mb-4">
                              <div className="flex items-center gap-3">
                                 <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${isGranted ? 'bg-green-100 dark:bg-green-900/30' : 'bg-gray-100 dark:bg-slate-700'
                                    }`}>
                                    {IconComponent && <IconComponent className={`w-5 h-5 ${isGranted ? 'text-green-600 dark:text-green-400' : 'text-gray-500 dark:text-slate-400'
                                       }`} />}
                                 </div>
                                 <div>
                                    <h4 className="font-semibold text-gray-800 dark:text-slate-100">{consent.title}</h4>
                                    <div className="flex items-center gap-2 mt-1">
                                       <Badge className={statusStyles.badge}>{statusStyles.text}</Badge>
                                       {consent.type === 'required' && (
                                          <Badge className="bg-purple-100 dark:bg-purple-900/30 text-purple-800 dark:text-purple-300">Required</Badge>
                                       )}
                                    </div>
                                 </div>
                              </div>
                           </div>

                           <p className="text-sm text-gray-600 dark:text-slate-300 mb-4">{consent.description}</p>

                           {consent.includes && (
                              <div className="mb-4">
                                 <p className="text-xs font-medium text-gray-500 dark:text-slate-400 mb-2">Includes:</p>
                                 <ul className="space-y-1">
                                    {consent.includes.slice(0, 3).map((item, idx) => (
                                       <li key={idx} className="text-xs text-gray-600 dark:text-slate-300 flex items-center gap-2">
                                          <Check className="w-3 h-3 text-green-500 dark:text-green-400" />
                                          {item}
                                       </li>
                                    ))}
                                    {consent.includes.length > 3 && (
                                       <li className="text-xs text-gray-400 dark:text-slate-500">+{consent.includes.length - 3} more...</li>
                                    )}
                                 </ul>
                              </div>
                           )}

                           <div className="flex gap-3 pt-4 border-t border-gray-100 dark:border-slate-700">
                              {!isGranted ? (
                                 <Button
                                    className="flex-1 bg-brand-medium hover:bg-brand-deep text-white"
                                    onClick={() => handleGrantClick(consent, 'grant')}
                                 >
                                    <Plus className="w-4 h-4 mr-2" />
                                    Grant Consent
                                 </Button>
                              ) : (
                                 <>
                                    <Button
                                       variant="outline"
                                       className="flex-1"
                                       onClick={() => handleGrantClick(consent, 'modify')}
                                    >
                                       <Edit3 className="w-4 h-4 mr-2" />
                                       Modify
                                    </Button>
                                    {consent.type !== 'required' && (
                                       <Button
                                          variant="outline"
                                          className="text-red-600 dark:text-red-400 border-red-300 dark:border-red-700 hover:bg-red-50 dark:hover:bg-red-900/20"
                                          onClick={() => handleWithdrawClick(consent)}
                                       >
                                          <X className="w-4 h-4 mr-2" />
                                          Withdraw
                                       </Button>
                                    )}
                                 </>
                              )}
                           </div>
                        </Card>
                     );
                  })}
               </div>

               {/* Quick Actions */}
               <Card className="p-6 border border-gray-100 dark:border-slate-700 shadow-soft">
                  <h3 className="text-lg font-semibold text-gray-800 dark:text-slate-100 mb-2 flex items-center gap-2">
                     <Plus className="w-5 h-5 text-gray-400 dark:text-slate-500" />
                     Share your data with a provider
                  </h3>
                  <p className="text-sm text-gray-600 dark:text-slate-300 mb-4">
                     Your assigned doctor can always see your clinical data. Other providers need your consent first.
                  </p>
                  <Button onClick={() => setShowNewConsent(true)}>
                     <Plus className="w-4 h-4 mr-2" />
                     Grant new consent
                  </Button>
               </Card>
            </>
         )}

         {/* Data Management Tab */}
         {!isLoading && !fetchError && activeTab === 'data' && (
            <>
               {/* Data Management Header */}
               <div className="bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 border border-purple-200 dark:border-purple-800 rounded-xl p-6">
                  <div className="flex items-start gap-4">
                     <div className="w-12 h-12 bg-purple-100 dark:bg-purple-900/30 rounded-xl flex items-center justify-center">
                        <Database className="w-6 h-6 text-purple-600 dark:text-purple-400" />
                     </div>
                     <div className="flex-1">
                        <h3 className="text-lg font-bold text-gray-800 dark:text-slate-100">Health Data Management</h3>
                        <p className="text-gray-600 dark:text-slate-300 mt-1">
                           Manage how your health data is stored, accessed, and shared.
                           You have full control over your personal health information.
                        </p>
                     </div>
                  </div>
               </div>

               {/* Data Overview Stats */}
               <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                  <Card className="p-4 border border-gray-100 dark:border-slate-700 shadow-soft">
                     <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-blue-100 dark:bg-blue-900/30 rounded-lg flex items-center justify-center">
                           <FileText className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div>
                           <p className="text-2xl font-bold text-gray-800 dark:text-slate-100">47</p>
                           <p className="text-sm text-gray-500 dark:text-slate-400">Medical Records</p>
                        </div>
                     </div>
                  </Card>
                  <Card className="p-4 border border-gray-100 dark:border-slate-700 shadow-soft">
                     <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-green-100 dark:bg-green-900/30 rounded-lg flex items-center justify-center">
                           <Users className="w-5 h-5 text-green-600 dark:text-green-400" />
                        </div>
                        <div>
                           <p className="text-2xl font-bold text-gray-800 dark:text-slate-100">3</p>
                           <p className="text-sm text-gray-500 dark:text-slate-400">Family Shared</p>
                        </div>
                     </div>
                  </Card>
                  <Card className="p-4 border border-gray-100 dark:border-slate-700 shadow-soft">
                     <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-orange-100 dark:bg-orange-900/30 rounded-lg flex items-center justify-center">
                           <Network className="w-5 h-5 text-orange-600 dark:text-orange-400" />
                        </div>
                        <div>
                           <p className="text-2xl font-bold text-gray-800 dark:text-slate-100">5</p>
                           <p className="text-sm text-gray-500 dark:text-slate-400">Connected Providers</p>
                        </div>
                     </div>
                  </Card>
                  <Card className="p-4 border border-gray-100 dark:border-slate-700 shadow-soft">
                     <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-purple-100 dark:bg-purple-900/30 rounded-lg flex items-center justify-center">
                           <Download className="w-5 h-5 text-purple-600 dark:text-purple-400" />
                        </div>
                        <div>
                           <p className="text-2xl font-bold text-gray-800 dark:text-slate-100">12</p>
                           <p className="text-sm text-gray-500 dark:text-slate-400">Data Exports</p>
                        </div>
                     </div>
                  </Card>
               </div>

               {/* Data Access Log */}
               <Card className="overflow-hidden border border-gray-100 dark:border-slate-700 shadow-soft">
                  <div className="px-6 py-5 border-b border-gray-100 dark:border-slate-700 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white dark:bg-slate-800">
                     <h3 className="text-lg font-bold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <BarChart3 className="w-5 h-5 text-gray-400 dark:text-slate-500" />
                        Data Access Log
                     </h3>
                     <div className="flex items-center gap-3">
                        <div className="relative">
                           <Search className="w-4 h-4 absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 dark:text-slate-500" />
                           <input
                              type="text"
                              placeholder="Search access logs..."
                              value={dataSearchQuery}
                              onChange={(e) => setDataSearchQuery(e.target.value)}
                              className="pl-9 pr-4 py-2 text-sm border border-gray-300 dark:border-slate-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                           />
                        </div>
                        <Button variant="outline" size="sm" className="p-2" title="Filter">
                           <Filter className="w-4 h-4" />
                        </Button>
                     </div>
                  </div>
                  <div className="divide-y divide-gray-100 dark:divide-slate-700">
                     {accessLog === null && (
                        <p className="px-6 py-4 text-sm text-gray-500">Loading access log...</p>
                     )}
                     {accessLogError && (
                        <p role="alert" className="px-6 py-4 text-sm text-red-600">Unable to load the access log.</p>
                     )}
                     {accessLog !== null && !accessLogError && accessLogView.length === 0 && (
                        <p className="px-6 py-4 text-sm text-gray-500">No one has accessed your records yet.</p>
                     )}
                     {accessLogView.map((log) => (
                        <div key={log.id} className="px-6 py-4 hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors">
                           <div className="flex items-center justify-between">
                              <div className="flex items-center gap-3">
                                 <div className={`w-8 h-8 rounded-full flex items-center justify-center ${log.type === 'view' ? 'bg-blue-100 dark:bg-blue-900/30' :
                                    log.type === 'add' ? 'bg-green-100 dark:bg-green-900/30' :
                                       log.type === 'sync' ? 'bg-purple-100 dark:bg-purple-900/30' :
                                          'bg-orange-100 dark:bg-orange-900/30'
                                    }`}>
                                    {log.type === 'view' && <Eye className="w-4 h-4 text-blue-600 dark:text-blue-400" />}
                                    {log.type === 'add' && <Plus className="w-4 h-4 text-green-600 dark:text-green-400" />}
                                    {log.type === 'sync' && <RefreshCw className="w-4 h-4 text-purple-600 dark:text-purple-400" />}
                                    {log.type === 'update' && <Edit3 className="w-4 h-4 text-orange-600 dark:text-orange-400" />}
                                 </div>
                                 <div>
                                    <p className="font-medium text-gray-800 dark:text-slate-100">{log.accessor}</p>
                                    <p className="text-sm text-gray-500 dark:text-slate-400">{log.action}</p>
                                 </div>
                              </div>
                              <span className="text-sm text-gray-400 dark:text-slate-500">{formatDateTime(log.timestamp)}</span>
                           </div>
                        </div>
                     ))}
                  </div>
                  <div className="px-6 py-4 bg-gray-50 dark:bg-slate-800/50 border-t border-gray-100 dark:border-slate-700">
                     <Button variant="outline" className="w-full">
                        View Full Access History
                     </Button>
                  </div>
               </Card>

               {/* Data Management Actions */}
               <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {/* Export Your Data */}
                  <Card className="p-6 border border-gray-100 dark:border-slate-700 shadow-soft">
                     <h3 className="font-semibold text-gray-800 dark:text-slate-100 mb-4 flex items-center gap-2">
                        <Download className="w-5 h-5 text-gray-400 dark:text-slate-500" />
                        Export Your Data
                     </h3>
                     <p className="text-sm text-gray-600 dark:text-slate-300 mb-4">
                        Download a complete copy of your health records in various formats for your personal use or to share with other providers.
                     </p>
                     <div className="space-y-3">
                        <button
                           className="w-full p-3 border border-gray-200 dark:border-slate-700 rounded-lg hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors flex items-center justify-between"
                           disabled title="Coming soon"
                        >
                           <div className="flex items-center gap-3">
                              <FileText className="w-5 h-5 text-red-500 dark:text-red-400" />
                              <span className="font-medium text-gray-800 dark:text-slate-100">Export as PDF <span className="text-xs text-gray-400">(coming soon)</span></span>
                           </div>
                           <ChevronDown className="w-4 h-4 text-gray-400 dark:text-slate-500 rotate-[-90deg]" />
                        </button>
                        <button
                           className="w-full p-3 border border-gray-200 dark:border-slate-700 rounded-lg hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors flex items-center justify-between"
                           disabled title="Coming soon"
                        >
                           <div className="flex items-center gap-3">
                              <Database className="w-5 h-5 text-blue-500 dark:text-blue-400" />
                              <span className="font-medium text-gray-800 dark:text-slate-100">Export as FHIR <span className="text-xs text-gray-400">(coming soon)</span></span>
                           </div>
                           <ChevronDown className="w-4 h-4 text-gray-400 dark:text-slate-500 rotate-[-90deg]" />
                        </button>
                        <button
                           className="w-full p-3 border border-gray-200 dark:border-slate-700 rounded-lg hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors flex items-center justify-between"
                           disabled title="Coming soon"
                        >
                           <div className="flex items-center gap-3">
                              <Archive className="w-5 h-5 text-green-500 dark:text-green-400" />
                              <span className="font-medium text-gray-800 dark:text-slate-100">Export as C-CDA <span className="text-xs text-gray-400">(coming soon)</span></span>
                           </div>
                           <ChevronDown className="w-4 h-4 text-gray-400 dark:text-slate-500 rotate-[-90deg]" />
                        </button>
                     </div>
                  </Card>

                  {/* Data Deletion Requests */}
                  <Card className="p-6 border border-gray-100 dark:border-slate-700 shadow-soft">
                     <h3 className="font-semibold text-gray-800 dark:text-slate-100 mb-4 flex items-center gap-2">
                        <Trash2 className="w-5 h-5 text-gray-400 dark:text-slate-500" />
                        Data Deletion Requests
                     </h3>
                     <p className="text-sm text-gray-600 dark:text-slate-300 mb-4">
                        Request deletion of specific health data. Note that some records must be retained by law for a minimum period.
                     </p>
                     <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4 mb-4">
                        <div className="flex items-start gap-2">
                           <AlertTriangle className="w-5 h-5 text-yellow-600 dark:text-yellow-400 flex-shrink-0 mt-0.5" />
                           <div>
                              <p className="text-sm font-medium text-yellow-800 dark:text-yellow-300">Important Notice</p>
                              <p className="text-xs text-yellow-700 dark:text-yellow-400 mt-1">
                                 Some medical records cannot be deleted due to legal retention requirements.
                                 A privacy officer will review all deletion requests.
                              </p>
                           </div>
                        </div>
                     </div>
                     <Button
                        variant="outline"
                        className="w-full text-red-600 dark:text-red-400 border-red-300 dark:border-red-700 hover:bg-red-50 dark:hover:bg-red-900/20"
                        disabled title="Coming soon"
                     >
                        <Trash2 className="w-4 h-4 mr-2" />
                        Request Data Deletion (coming soon)
                     </Button>
                  </Card>
               </div>

            </>
         )}

         {/* Withdraw Consent Modal */}
         {showWithdrawModal && selectedConsent && (
            <Modal
               isOpen={showWithdrawModal}
               onClose={() => setShowWithdrawModal(false)}
               title={`Withdraw Consent for ${selectedConsent.title}`}
            >
               <div className="p-6 space-y-6">
                  {/* Warning Banner */}
                  <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                     <div className="flex items-start gap-3">
                        <AlertTriangle className="w-5 h-5 text-red-600 dark:text-red-400 flex-shrink-0 mt-0.5" />
                        <div>
                           <h4 className="font-semibold text-red-800 dark:text-red-300">Please review before proceeding</h4>
                           <p className="text-sm text-red-700 dark:text-red-400 mt-1">
                              You are about to withdraw your consent for "{selectedConsent.title}".
                           </p>
                        </div>
                     </div>
                  </div>

                  {/* What will happen */}
                  <div>
                     <h4 className="font-semibold text-gray-800 dark:text-slate-100 mb-2">What this means:</h4>
                     <ul className="space-y-2 text-sm text-gray-600 dark:text-slate-300">
                        <li className="flex items-start gap-2">
                           <X className="w-4 h-4 text-red-500 dark:text-red-400 flex-shrink-0 mt-0.5" />
                           Your information will no longer be used for this purpose going forward
                        </li>
                        <li className="flex items-start gap-2">
                           <Info className="w-4 h-4 text-blue-500 dark:text-blue-400 flex-shrink-0 mt-0.5" />
                           This will not affect your quality of care
                        </li>
                        <li className="flex items-start gap-2">
                           <AlertTriangle className="w-4 h-4 text-yellow-500 dark:text-yellow-400 flex-shrink-0 mt-0.5" />
                           Data already used under this consent cannot be retrieved
                        </li>
                     </ul>
                  </div>

                  {/* Effective Date */}
                  <div>
                     <label className="block text-sm font-medium text-gray-700 dark:text-slate-200 mb-2">
                        When should this take effect?
                     </label>
                     <div className="space-y-2">
                        <label className="flex items-center gap-2">
                           <input
                              type="radio"
                              name="effective"
                              value="immediately"
                              checked={withdrawEffective === 'immediately'}
                              onChange={(e) => setWithdrawEffective(e.target.value)}
                              className="text-blue-600 dark:text-blue-400"
                           />
                           <span className="text-sm text-gray-700 dark:text-slate-200">Immediately</span>
                        </label>
                     </div>
                  </div>

                  {/* Reason (optional) */}
                  <div>
                     <label className="block text-sm font-medium text-gray-700 dark:text-slate-200 mb-2">
                        Reason for withdrawal (optional)
                     </label>
                     <textarea
                        value={withdrawReason}
                        onChange={(e) => setWithdrawReason(e.target.value)}
                        className="w-full border border-gray-300 dark:border-slate-600 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                        rows={3}
                        placeholder="You may provide a reason for your records..."
                     />
                  </div>

                  {/* Confirmation checkbox */}
                  <div>
                     <label className="flex items-start gap-2">
                        <input
                           type="checkbox"
                           checked={withdrawConfirmed}
                           onChange={(e) => setWithdrawConfirmed(e.target.checked)}
                           className="mt-1 text-blue-600 dark:text-blue-400"
                        />
                        <span className="text-sm text-gray-700 dark:text-slate-200">
                           I understand the implications of withdrawing this consent
                        </span>
                     </label>
                  </div>

                  {/* Action Buttons */}
                  <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-slate-700">
                     <Button variant="outline" onClick={() => setShowWithdrawModal(false)}>
                        Cancel
                     </Button>
                     <Button
                        className="bg-red-600 hover:bg-red-700 text-white"
                        disabled={!withdrawConfirmed}
                        onClick={confirmWithdraw}
                     >
                        Confirm Withdrawal
                     </Button>
                  </div>
               </div>
            </Modal>
         )}

         <NewConsentModal
            isOpen={showNewConsent}
            onClose={() => setShowNewConsent(false)}
            onGranted={handleNewConsentGranted}
         />

         {/* Grant/Modify Consent Slide-in Panel */}
         <GrantModifyConsent
            isOpen={showGrantModal}
            onClose={() => {
               setShowGrantModal(false);
               setSelectedConsent(null);
            }}
            category={getFormCategory()}
            mode={consentMode}
            existingSelections={consentMode === 'modify' && selectedConsent ? {
               [selectedConsent.id]: true
            } : {}}
            onSubmit={handleConsentSubmit}
            consentId={selectedConsent?.id}
            grantedToId={selectedConsent?.grantedTo?.userId}
            backendConsentType={selectedConsent?.consentType}
         />
      </div>
   );
};

export default ConsentManagement;
